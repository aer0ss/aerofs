package com.aerofs.trifrost.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableMap;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang.StringUtils;

import java.util.Map;

/**
 * Input type for /users/verify route
 */
public class DeviceAuthentication {
    public static DeviceAuthentication createForAuthCode(String email, String authCode) {
        DeviceAuthentication auth = new DeviceAuthentication();
        auth.grantType = GrantType.AuthCode;
        auth.email = email;
        auth.authCode = authCode;
        return auth;
    }

    public enum GrantType {
        AuthCode;

        @JsonCreator
        public static GrantType forValue(String value) { return namesMap.get(StringUtils.lowerCase(value)); }

        @JsonValue
        public String toValue() {
            for (Map.Entry<String, GrantType> entry : namesMap.entrySet()) {
                if (entry.getValue() == this) return entry.getKey();
            }
            return null;
        }

        private static Map<String, GrantType> namesMap = ImmutableMap.<String, GrantType>builder()
                .put("auth_code", AuthCode)
                .build();
    }

    @SuppressWarnings("unused")  // Jackson-compatibility
    private DeviceAuthentication() { }

    @ApiModelProperty("must be 'auth_code' or 'refresh_token'")
    public GrantType grantType;

    // these two required for grantType == AuthCode
    @ApiModelProperty("auth code received by email")
    public String authCode;
    @ApiModelProperty("required if grant_type = 'auth_code'")
    public String email;
}
