package io.singularitylab.soundspleeter;

import java.math.BigInteger;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.protobuf.Message;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Timer;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.singularitynet.sdk.client.Configuration;
import io.singularitynet.sdk.client.ConfigurationUtils;
import io.singularitynet.sdk.client.Sdk;
import io.singularitynet.sdk.paymentstrategy.FixedPaymentChannelPaymentStrategy;
import io.singularitynet.sdk.client.ServiceClient;
import io.singularitynet.sdk.daemon.FixedGroupEndpointSelector;
import io.singularitynet.sdk.daemon.GrpcSettings;

import io.singularitynet.service.soundspleeter.SoundSpleeterGrpc;
import io.singularitynet.service.soundspleeter.SoundSpleeterGrpc.SoundSpleeterImplBase;
import io.singularitynet.service.soundspleeter.SoundSpleeterGrpc.SoundSpleeterStub;
import io.singularitynet.service.soundspleeter.SoundSpleeterOuterClass.Input;
import io.singularitynet.service.soundspleeter.SoundSpleeterOuterClass.Output;

public class Proxy extends SoundSpleeterImplBase {

    private static final Logger log = LoggerFactory.getLogger(Proxy.class);

    private final Sdk sdk;
    private final Channel[] channels;

    private final Object lock = new Object();
    private int nextChannelIndex;

    private final ExecutorService executor;

    private final Timer timeInQueue;
    private final Timer processingTime;
    private final Counter pendingTasks;
    private final Histogram pendingTasksHist;
    private final Counter rejectedTasks;

    private final AtomicInteger nextRequestId = new AtomicInteger(0);

    public Proxy(MetricRegistry metrics, ExecutorService executor,
            long[] channelIds, Properties props) {
        String orgId = props.getProperty(Config.ORGANIZATION_ID);
        String serviceId = props.getProperty(Config.SERVICE_ID);
        String paymentGroupId = props.getProperty(Config.PAYMENT_GROUP_ID);
        log.info("orgId: {}, serviceId: {}, paymentGroupId: {}",
                orgId, serviceId, paymentGroupId);

        this.channels = new Channel[channelIds.length];
        this.nextChannelIndex = 0;
        
        Configuration config = ConfigurationUtils.fromProperties(props);
        this.sdk = new Sdk(config);

        for (int i = 0; i < channelIds.length; i++) {
            long channelId = channelIds[i];

            FixedPaymentChannelPaymentStrategy paymentStrategy = 
                new FixedPaymentChannelPaymentStrategy(BigInteger.valueOf(channelId));

            int maxInboundMessageSize = Integer.parseInt(
                    props.getProperty(Config.MAX_INBOUND_MESSAGE_SIZE));
            GrpcSettings grpcSettings = GrpcSettings.newBuilder()
                .setMaxInboundMessageSize(maxInboundMessageSize)
                .build();
            ServiceClient serviceClient = sdk.newServiceClient(orgId,
                    serviceId, new FixedGroupEndpointSelector(paymentGroupId),
                    paymentStrategy, grpcSettings);

            SoundSpleeterStub stub = serviceClient.getGrpcStub(SoundSpleeterGrpc::newStub);
            this.channels[i] = new Channel(channelId, serviceClient, stub);
        }

        this.executor = executor;

        this.timeInQueue = metrics.timer(MetricRegistry.name(Proxy.class, "timeInQueue"));
        this.processingTime = metrics.timer(MetricRegistry.name(Proxy.class, "processingTime"));
        this.pendingTasks = metrics.counter(MetricRegistry.name(Proxy.class, "pendingTasks"));
        this.pendingTasksHist = metrics.histogram(MetricRegistry.name(Proxy.class, "pendingTasksHist"));
        this.rejectedTasks = metrics.counter(MetricRegistry.name(Proxy.class, "rejectedTasks"));
    }

    public int getNumberOfChannels() {
        return channels.length;
    }

    public void close() {
        for (Channel channel : channels) {
            channel.serviceClient.close();
        }
        sdk.close();
    }

    public void spleeter(Input request, StreamObserver<Output> responseObserver) {
        int id = nextRequestId.incrementAndGet();
        log.info("request[{}] received {} bytes", id, request.toByteArray().length);
        log.trace("request[{}]: {}", id, request);
        try {
            executor.submit(new SpleeterTask(id, request, responseObserver));
            pendingTasks.inc();
            pendingTasksHist.update(pendingTasks.getCount());
        } catch(RejectedExecutionException e) {
            rejectedTasks.inc();
            responseObserver.onError(errorResourceExhausted("Task queue is full"));
        }
    }

    private static StatusException errorResourceExhausted(String description) {
        return Status.RESOURCE_EXHAUSTED.withDescription(description).asException();
    }

    private class SpleeterTask implements Runnable {
        
        private final int id;
        private final Input request;
        private final StreamObserver<Output> responseObserver;
        private final Timer.Context queueTimer;

        public SpleeterTask(int id, Input request, StreamObserver<Output> responseObserver) {
            this.id = id;
            this.request = request;
            this.responseObserver = responseObserver;
            this.queueTimer = timeInQueue.time();
        }

        public void run() {
            log.info("request[{}] processing started", id);
            pendingTasks.dec();
            pendingTasksHist.update(pendingTasks.getCount());
            queueTimer.close();
            try (final Timer.Context processingTimer = processingTime.time()) {
                Channel channel;
                synchronized(lock) {
                    channel = acquireChannel();
                }
                if (channel == null) {
                    responseObserver.onError(
                            errorResourceExhausted("No payment channel available"));
                    return;
                }
                log.info("use channel {} to handle request[{}]", channel.id, id); 
                try {
                    ObserverWrapper<Output> observer = new ObserverWrapper<>(id, responseObserver);
                    channel.stub.spleeter(request, observer);
                    try {
                        observer.awaitCompleted();
                    } catch(InterruptedException e) {
                        log.error("request[{}] interrupted", id);
                    }
                } finally {
                    synchronized(lock) {
                        channel.release();
                    }
                }
            }
        }
    }

    private Channel acquireChannel() {
        int startIndex = nextChannelIndex;
        do {
            Channel channel = channels[nextChannelIndex];
            nextChannelIndex = (nextChannelIndex + 1) % channels.length;
            if (channel.acquire()) {
                return channel;
            }
        } while (startIndex != nextChannelIndex);
        return null;
    }

    private static class Channel {
        final long id;
        final ServiceClient serviceClient;
        final SoundSpleeterStub stub;
        boolean acquired;

        public Channel(long id, ServiceClient serviceClient, SoundSpleeterStub stub) {
            this.id = id;
            this.serviceClient = serviceClient;
            this.stub = stub;
            this.acquired = false;
        }

        public boolean acquire() {
            if (acquired) {
                return false;
            }
            acquired = true;
            return true;
        }

        public void release() {
            acquired = false;
        }
    }

    private static class ObserverWrapper<T extends Message> implements StreamObserver<T> {

        private final int id;
        private final StreamObserver<T> delegate;
        private final CountDownLatch completed;

        public ObserverWrapper(int id, StreamObserver<T> delegate) {
            this.id = id;
            this.delegate = delegate;
            this.completed = new CountDownLatch(1);
        }

        public void onCompleted() {
            callDelegateAndComplete(() -> {
                log.info("request[{}] completed", id);
                delegate.onCompleted();
            });
        }

        private boolean isCallAlreadyCancelled(Throwable t) {
            boolean error = true;
            if (t instanceof StatusRuntimeException) {
                StatusRuntimeException e = (StatusRuntimeException) t;
                if (Status.CANCELLED.getCode().equals(e.getStatus().getCode())) {
                    error = false;
                }
            }
            return error;
        }

        private void logError(String message, int id, Throwable t) {
            if (isCallAlreadyCancelled(t)) {
                log.error(message, id, t);
            } else {
                log.info("NOT AN ISSUE: " + message, id, t);
            }
        }

        public void onError(Throwable t) {
            callDelegateAndComplete(() -> {
                logError("request[{}] completed with error", id, t);
                delegate.onError(t);
            });
        }

        public void onNext(T value) {
            log.info("request[{}] result {} bytes", id, value.toByteArray().length);
            delegate.onNext(value);
        }

        private void callDelegateAndComplete(Runnable callback) {
            try {
                callback.run();
            } catch (Throwable t) {
                logError("request[{}] error in call to delegate", id, t);
            } finally {
                completed.countDown();
            }
        }

        public void awaitCompleted() throws InterruptedException {
            completed.await();
        }
    }

}
