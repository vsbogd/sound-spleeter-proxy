package io.singularitynet.soundspleeter;

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
            System.out.println("Usage: App <config_name>");
            System.exit(1);
        }

        Properties props = loadConfig(args[0]);

        Proxy handler = new Proxy(props);
        try {
            Server server = ServerBuilder
                .forPort(Integer.decode(props.getProperty("proxy.port")))
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
        Properties props = new Properties();
        InputStream is = App.class.getClassLoader()
            .getResourceAsStream(configName);
        props.load(is);
        return props;
    }

}
