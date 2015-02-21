package com.aerofs.polaris.api;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;

import javax.annotation.Nullable;

public abstract class PolarisUtilities {

    public static final String VERKEHR_UPDATE_TOPIC_PREFIX = "pol/";

    public static String getVerkehrUpdateTopic(String root) {
        return VERKEHR_UPDATE_TOPIC_PREFIX + root;
    }

    public static String hexEncode(byte[] bytes) {
        return BaseEncoding.base16().lowerCase().encode(bytes);
    }

    public static byte[] hexDecode(String hex) {
        return BaseEncoding.base16().lowerCase().decode(hex.toLowerCase().trim());
    }

    public static @Nullable byte[] stringToUTF8Bytes(@Nullable String string) {
        return string != null ? string.getBytes(Charsets.UTF_8) : null;
    }

    public static @Nullable String stringFromUTF8Bytes(@Nullable byte[] string) {
        return string != null ? new String(string, Charsets.UTF_8) : null;
    }

    private PolarisUtilities() {
        // to prevent instantiation by subclasses
    }
}
