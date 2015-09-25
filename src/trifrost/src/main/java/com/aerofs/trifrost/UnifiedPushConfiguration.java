package com.aerofs.trifrost;

import com.aerofs.trifrost.api.Device;
import org.apache.commons.codec.binary.Base64;

public class UnifiedPushConfiguration {
    private String androidVariantId;
    private String androidVariantSecret;
    private String iosVariantId;
    private String iosVariantSecret;
    private String serverURL;

    public static enum Variant { ANDROID, IOS };

    public String getAndroidVariantId() {
        return androidVariantId;
    }

    public void setAndroidVariantId(String androidVariantId) {
        this.androidVariantId = androidVariantId;
    }

    public String getAndroidVariantSecret() {
        return androidVariantSecret;
    }

    public void setAndroidVariantSecret(String androidVariantSecret) {
        this.androidVariantSecret = androidVariantSecret;
    }

    public String getIosVariantId() {
        return iosVariantId;
    }

    public void setIosVariantId(String iosVariantId) {
        this.iosVariantId = iosVariantId;
    }

    public String getIosVariantSecret() {
        return iosVariantSecret;
    }

    public void setIosVariantSecret(String iosVariantSecret) {
        this.iosVariantSecret = iosVariantSecret;
    }

    public String getServerURL() {
        return serverURL;
    }

    public void setServerURL(String serverURL) {
        this.serverURL = serverURL;
    }

    public String getBasicAuthToken(Device.PushType pushType) {
        String token = "";
        if (pushType == null) {
            return token;
        }
        switch (pushType) {
            case APNS:
                token = getBasicAuthToken(Variant.IOS);
                break;
            case GCM:
                token = getBasicAuthToken(Variant.ANDROID);
                break;
        }
        return token;
    }

    public String getBasicAuthToken(Variant variant) {
        String token = "";
        if (variant == null) {
            return token;
        }
        switch (variant) {
            case ANDROID:
                token = String.format("%s:%s", getAndroidVariantId(), getAndroidVariantSecret());
                break;
            case IOS:
                token = String.format("%s:%s", getIosVariantId(), getIosVariantSecret());
                break;
        }
        return Base64.encodeBase64String(token.getBytes());
    }
}
