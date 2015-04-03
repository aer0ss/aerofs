package com.aerofs.auth.client.delegated;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;

/**
 * Client-side convenience headers, constants and
 * functions specific to the "Aero-Delegated-User"
 * HTTP authentication scheme.
 */
public abstract class AeroDelegatedUser {

    private static final String AERO_DELEGATED_USER_HEADER_PRINTF_FORMAT_STRING = "Aero-Delegated-User %s %s %s";

    /**
     * Get a properly-formatted value for an "Authorization"
     * header with the scheme "Aero-Delegated-User".
     *
     * @param service service making the request
     * @param deploymentSecret AeroFS shared deployment secret
     * @param user user on whose behalf the service is making the request
     * @return a valid value for the "Authorization" header with the scheme "Aero-Delegated-User"
     */
    public static String getHeaderValue(String service, String deploymentSecret, String user) {
        return String.format(AERO_DELEGATED_USER_HEADER_PRINTF_FORMAT_STRING, service, deploymentSecret, BaseEncoding.base64().encode(user.getBytes(Charsets.UTF_8)));
    }

    private AeroDelegatedUser() {
        // to prevent instantiation by subclasses
    }
}
