package io.singularitynet.soundspleeter;

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
        int proxyPort = Integer.parseInt(props.getProperty("proxy.port"));
        int maxInboundMessageSize = Integer.parseInt(
                props.getProperty("max.inbound.message.size"));

        Proxy handler = new Proxy(props);
        try {
            Server server = ServerBuilder
                .forPort(proxyPort)
                .maxInboundMessageSize(maxInboundMessageSize)
                .addService(handler)
                .build();
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

}
