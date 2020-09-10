package io.singularitylab.soundspleeter;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;

import io.grpc.ServerBuilder;
import io.grpc.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Slf4jReporter;

public class App {

    static {
        setSystemPropertyIfEmpty("org.slf4j.simpleLogger.showDateTime", "true");
        setSystemPropertyIfEmpty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd' 'HH:mm:ss.SSS");
    }

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private static final MetricRegistry metrics = new MetricRegistry();

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: App [local|<config_file_name>]");
            System.exit(1);
        }

        Properties props = loadConfig(args[0]);

        Proxy handler = null;
        ExecutorService executor = null;
        try {
            long[] channelIds = readChannelIds(props);
            log.info("payment channel ids to be used: {}", channelIds);
            executor = newExecutor(channelIds.length,
                    Integer.parseInt(props.getProperty(Config.QUEUE_SIZE, "1000")));
            handler = new Proxy(metrics, executor, channelIds, props);
            Server server = initGrpcServer(props)
                .addService(handler)
                .build();
            server.start();
            startMetricsReport(props);
            log.info("server started");
            server.awaitTermination();
        } finally {
            if (executor != null) {
                executor.shutdownNow();
            }
            if (handler != null) {
                handler.close();
            }
            log.info("server stopped");
        }
    }

    private static Properties loadConfig(String configName) throws IOException {
        InputStream is;
        if (configName.equals("local")) {
            is = App.class.getClassLoader()
                .getResourceAsStream("local.properties");
        } else {
            is = new FileInputStream(configName);
        }

        Properties props = new Properties();
        props.load(is);
        return props;
    }

    private static ServerBuilder initGrpcServer(Properties props) {
        int proxyPort = Integer.parseInt(props.getProperty(Config.PROXY_PORT));
        log.info("listening port: {}", proxyPort);

        int maxInboundMessageSize = Integer.parseInt(
                props.getProperty(Config.MAX_INBOUND_MESSAGE_SIZE));
        log.info("max inbound message size: {}", maxInboundMessageSize);

        ServerBuilder builder = ServerBuilder
            .forPort(proxyPort)
            .maxInboundMessageSize(maxInboundMessageSize);

        if (props.getProperty(Config.DOMAIN_CERTIFICATE) != null) {
            String certificate = props.getProperty(Config.DOMAIN_CERTIFICATE);
            String private_key = props.getProperty(Config.DOMAIN_PRIVATE_KEY);
            log.info("using transport security: " + 
                    "certificate file: {}, " +
                    "private key file: {}", certificate, private_key);
            builder.useTransportSecurity(
                    new File(certificate),
                    new File(private_key));
        }

        return builder;
    }
    
    private static ExecutorService newExecutor(int threads, int queueSize) {
        ArrayBlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(queueSize);
        metrics.register(MetricRegistry.name(Proxy.class, "requests", "size"),
                new Gauge<Integer>() {
                    @Override
                    public Integer getValue() {
                        return queue.size();
                    }
                });
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.SECONDS,
                queue);
    }
    
    private static void setSystemPropertyIfEmpty(String property, String value) {
        if (System.getProperty(property) == null) {
            System.setProperty(property, value);
        }
    }

    private static void startMetricsReport(Properties props) {
        int reportPeriodInSeconds = Integer.parseInt(
                props.getProperty(Config.REPORT_PERIOD_IN_SECONDS, "30"));
        Slf4jReporter reporter = Slf4jReporter.forRegistry(metrics)
            .outputTo(LoggerFactory.getLogger("io.singularitylab.soundspleeter.metrics"))
            .convertRatesTo(TimeUnit.SECONDS)
            .convertDurationsTo(TimeUnit.MILLISECONDS)
            .build();
        reporter.start(reportPeriodInSeconds, TimeUnit.SECONDS);
    }

    private static long[] readChannelIds(Properties props) {
        int length = Integer.decode(props.getProperty(Config.CHANNEL_COUNT));
        long[] result = new long[length];
        for (int i = 0; i < length; ++i) {
            result[i] = Long.decode(props.getProperty(Config.getChannel(i)));
        }
        return result;
    }
}
