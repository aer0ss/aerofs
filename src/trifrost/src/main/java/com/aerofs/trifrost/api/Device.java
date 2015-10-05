package com.aerofs.trifrost.api;

import com.google.common.base.Strings;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;

public class Device {
    @NotNull
    @ApiModelProperty("device name, unique per user")
    public String name;
    @NotNull
    @ApiModelProperty("general category (ios, android, web)")
    public String family;
    @ApiModelProperty("must be one of 'apns', 'gcm'")
    public PushType pushType;
    @ApiModelProperty("required if push_type is provided")
    public String pushToken;

    public enum PushType {
        APNS, GCM, NONE;
        @Override
        public String toString () {
            return name().toLowerCase();
        }
    }

    public Device(String name, String family) throws IllegalArgumentException {
        this(name, family, null, null);
    }

    public Device(String name, String family, PushType pushType, String pushToken) throws IllegalArgumentException {
        if (isValidPushCombination(pushType, pushToken)) {
            setName(name);
            setFamily(family);
            setPushType(pushType);
            setPushToken(pushToken);
        } else {
            throw new IllegalArgumentException("If pushType is provided and not equal to PushType.NONE, pushToken must also be supplied. pushToken may not be provided without pushType.");
        }
    }

    @SuppressWarnings("unused") // Jackson compatibility
    private Device() { }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public PushType getPushType() {
        return pushType;
    }

    public void setPushType(PushType pushType) {
        this.pushType = pushType;
    }

    public String getPushToken() {
        return pushToken;
    }

    public void setPushToken(String pushToken) {
        this.pushToken = ((pushType != null) && (pushToken != null) && (pushType == PushType.APNS))
                ? parsePushToken(pushToken) : pushToken;
    }

    public static boolean isValidPushCombination (PushType type, String token) {
        return
            (type == null && token == null) ||
            (type == PushType.NONE && token == null) ||
            (type != PushType.NONE && !Strings.isNullOrEmpty(token));
    }

    protected static String parsePushToken (String token) {
        return (token == null) ? null : token.toLowerCase().replaceAll("[^0-9a-f]", "");
    }
}
