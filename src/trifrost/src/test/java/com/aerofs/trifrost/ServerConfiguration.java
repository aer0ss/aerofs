package com.aerofs.trifrost;

import com.aerofs.baseline.http.HttpConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

public abstract class ServerConfiguration {

    public static final HttpConfiguration SERVICE = new HttpConfiguration();
    static {
        SERVICE.setHost("localhost");
        SERVICE.setPort((short) 9999);
        SERVICE.setMaxAcceptQueueSize(10);
        SERVICE.setNumNetworkThreads(2);
    }

    public static final HttpConfiguration ADMIN = new HttpConfiguration();
    static {
        ADMIN.setHost("localhost");
        ADMIN.setPort((short) 8888);
        ADMIN.setMaxAcceptQueueSize(10);
        ADMIN.setNumNetworkThreads(2);
    }

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
    }

    public static String signupUrl() {
        return String.format("http://%s:%s/auth/auth_code", SERVICE.getHost(), SERVICE.getPort());
    }

    public static String inviteUrl(String emailAddr) {
        return String.format("http://%s:%s/invite/%s", SERVICE.getHost(), SERVICE.getPort(), emailAddr);
    }

    public static String authTokenUrl() {
        return String.format("http://%s:%s/auth/token", SERVICE.getHost(), SERVICE.getPort());
    }

    public static String refreshTokenUrl() {
        return String.format("http://%s:%s/auth/refresh", SERVICE.getHost(), SERVICE.getPort());
    }

    public static String deviceUrl(String deviceId) {
        return String.format("http://%s:%s/devices/%s", SERVICE.getHost(), SERVICE.getPort(), deviceId);
    }
}
