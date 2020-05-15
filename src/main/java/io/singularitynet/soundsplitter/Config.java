package io.singularitynet.soundspleeter;

public class Config {

    public static final String PROXY_PORT = "proxy.port";
    public static final String MAX_INBOUND_MESSAGE_SIZE = "max.inbound.message.size";
    public static final String DOMAIN_CERTIFICATE = "domain.certificate";
    public static final String DOMAIN_PRIVATE_KEY = "domain.private.key";

    public static final String ORGANIZATION_ID = "organization.id";
    public static final String SERVICE_ID = "service.id";
    public static final String PAYMENT_GROUP_ID = "payment.group.id";

    public static final String CHANNEL_COUNT = "channel.count";

    public static String getChannel(long i) {
        return "channel." + Long.toString(i);
    }

}
