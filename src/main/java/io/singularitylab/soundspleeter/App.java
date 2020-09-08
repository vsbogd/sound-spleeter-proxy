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

public class App {

    static {
        setSystemPropertyIfEmpty("org.slf4j.simpleLogger.showDateTime", "true");
        setSystemPropertyIfEmpty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd' 'HH:mm:ss.SSS");
    }

    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: App [local|<config_file_name>]");
            System.exit(1);
        }

        Properties props = loadConfig(args[0]);

        Proxy handler = null;
        ExecutorService executor = null;
        try {
            handler = new Proxy(props);
            executor = newExecutor(handler.getNumberOfChannels(),
                    Integer.parseInt(props.getProperty(Config.QUEUE_SIZE, "1000")));
            Server server = initGrpcServer(props)
                .addService(handler)
                .executor(executor)
                .build();
            server.start();
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
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(queueSize));
    }
    
    private static void setSystemPropertyIfEmpty(String property, String value) {
        if (System.getProperty(property) == null) {
            System.setProperty(property, value);
        }
    }

}
