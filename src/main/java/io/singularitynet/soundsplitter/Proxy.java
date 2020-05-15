package io.singularitynet.soundspleeter;

import io.grpc.stub.StreamObserver;
import java.math.BigInteger;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.singularitynet.sdk.common.Utils;
import io.singularitynet.sdk.client.Configuration;
import io.singularitynet.sdk.client.ConfigurationUtils;
import io.singularitynet.sdk.client.Sdk;
import io.singularitynet.sdk.paymentstrategy.FixedPaymentChannelPaymentStrategy;
import io.singularitynet.sdk.client.ServiceClient;

import io.singularitynet.service.soundspleeter.SoundSpleeterGrpc;
import io.singularitynet.service.soundspleeter.SoundSpleeterGrpc.SoundSpleeterImplBase;
import io.singularitynet.service.soundspleeter.SoundSpleeterGrpc.SoundSpleeterStub;
import io.singularitynet.service.soundspleeter.SoundSpleeterOuterClass.Input;
import io.singularitynet.service.soundspleeter.SoundSpleeterOuterClass.Output;

public class Proxy extends SoundSpleeterImplBase {

    private static final Logger log = LoggerFactory.getLogger(SoundSpleeterStub.class);

    private final String orgId;
    private final String serviceId;
    private final String paymentGroupId;
    private final long[] channelIds;

    private final Sdk sdk;
    private final ServiceClient[] serviceClients;
    private final SoundSpleeterStub[] stubs;

    private int nextStubIndex;

    public Proxy(Properties props) {
        this.orgId = props.getProperty(Config.ORGANIZATION_ID);
        this.serviceId = props.getProperty(Config.SERVICE_ID);
        this.paymentGroupId = props.getProperty(Config.PAYMENT_GROUP_ID);
        log.info("orgId: {}, serviceId: {}, paymentGroupId: {}",
                orgId, serviceId, paymentGroupId);
        this.channelIds = readChannelIds(props);
        log.info("payment channel ids to be used: {}", channelIds);

        this.serviceClients = new ServiceClient[channelIds.length];
        this.stubs = new SoundSpleeterStub[channelIds.length];
        this.nextStubIndex = 0;
        
        Configuration config = ConfigurationUtils.fromProperties(props);
        this.sdk = new Sdk(config);

        for (int i = 0; i < channelIds.length; i++) {
            long channelId = channelIds[i];

            FixedPaymentChannelPaymentStrategy paymentStrategy = 
                new FixedPaymentChannelPaymentStrategy(sdk, BigInteger.valueOf(channelId));

            ServiceClient serviceClient = sdk.newServiceClient(orgId,
                    "sound-spleeter", "default_group", paymentStrategy);
            serviceClients[i] = serviceClient;

            SoundSpleeterStub stub = serviceClient.getGrpcStub(SoundSpleeterGrpc::newStub);
            stubs[i] = stub;
        }
    }

    private static long[] readChannelIds(Properties props) {
        int length = Integer.decode(props.getProperty(Config.CHANNEL_COUNT));
        long[] result = new long[length];
        for (int i = 0; i < length; ++i) {
            result[i] = Long.decode(props.getProperty(Config.getChannel(i)));
        }
        return result;
    }


    public void close() {
        for (ServiceClient client : serviceClients) {
            client.close();
        }
        sdk.close();
    }

    public void spleeter(Input request, StreamObserver<Output> responseObserver) {
        log.info("request received");
        log.debug("request: {}", request);
        SoundSpleeterStub stub = selectStub();
        stub.spleeter(request, responseObserver);
    }

    private SoundSpleeterStub selectStub() {
        log.info("use channel {} to handle request", channelIds[nextStubIndex]); 
        SoundSpleeterStub stub = stubs[nextStubIndex];
        nextStubIndex = (nextStubIndex + 1) % stubs.length;
        return stub;
    }

}
