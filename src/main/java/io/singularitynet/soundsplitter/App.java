package io.singularitynet.soundspleeter;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;

import io.grpc.ServerBuilder;
import io.grpc.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: App [local|<config_file_name>]");
            System.exit(1);
        }

        Properties props = loadConfig(args[0]);

        Proxy handler = new Proxy(props);
        try {
            Server server = initGrpcServer(props, handler);
            server.start();
            log.info("server started");
            server.awaitTermination();
        } finally {
            handler.close();
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

    private static Server initGrpcServer(Properties props, Proxy handler) {
        int proxyPort = Integer.parseInt(props.getProperty(Config.PROXY_PORT));
        log.info("listening port: {}", proxyPort);

        int maxInboundMessageSize = Integer.parseInt(
                props.getProperty(Config.MAX_INBOUND_MESSAGE_SIZE));
        log.info("max inbound message size: {}", maxInboundMessageSize);

        ServerBuilder builder = ServerBuilder
            .forPort(proxyPort)
            .maxInboundMessageSize(maxInboundMessageSize)
            .addService(handler);

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

        return builder.build();
    }

}
