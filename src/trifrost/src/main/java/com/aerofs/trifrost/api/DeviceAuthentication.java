package com.aerofs.trifrost.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang.StringUtils;

import java.util.Map;

/**
 * Input type for /users/verify route
 */
public class DeviceAuthentication {
    private static final Device DEFAULT_DEVICE = new Device("", "");

    public static DeviceAuthentication createForAuthCode(String email, String authCode, Device device) {
        DeviceAuthentication auth = new DeviceAuthentication();
        auth.grantType = GrantType.AuthCode;
        auth.email = email;
        auth.authCode = authCode;
        auth.device = device == null? DEFAULT_DEVICE : device;
        return auth;
    }

    public static DeviceAuthentication createForRefreshCode(String refreshToken, String userId, Device device) {
        DeviceAuthentication auth = new DeviceAuthentication();
        auth.grantType = GrantType.RefreshToken;
        auth.refreshToken = refreshToken;
        auth.userId= userId;
        auth.device = device == null? DEFAULT_DEVICE : device;
        return auth;
    }

    public enum GrantType {
        AuthCode,
        RefreshToken;

        @JsonCreator
        public static GrantType forValue(String value) { return namesMap.get(StringUtils.lowerCase(value)); }

        @JsonValue
        public String toValue() {
            for (Map.Entry<String, GrantType> entry : namesMap.entrySet()) {
                if (entry.getValue() == this) return entry.getKey();
            }
            return null;
        }

        // string values to be used in JSON requests
        private static Map<String, GrantType> namesMap = ImmutableMap.<String, GrantType>builder()
                .put("auth_code", AuthCode)
                .put("refresh_token", RefreshToken)
                .build();
    }

    @SuppressWarnings("unused")  // Jackson-compatibility
    private DeviceAuthentication() { }

    public GrantType grantType;

    // these two required for grantType == AuthCode
    public String authCode;
    public String email;

    // these two required for grantType == RefreshToken
    public String refreshToken;
    public String userId;

    // optional
    public Device device;
}
