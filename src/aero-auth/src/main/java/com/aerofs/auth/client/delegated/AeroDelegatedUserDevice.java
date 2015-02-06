package com.aerofs.auth.client.delegated;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;

/**
 * Client-side convenience headers, constants and
 * functions specific to the "Aero-Delegated-User-Device"
 * HTTP authentication scheme.
 */
public abstract class AeroDelegatedUserDevice {

    private static final String AERO_DELEGATED_USER_DEVICE_HEADER_PRINTF_FORMAT_STRING = "Aero-Delegated-User-Device %s %s %s %s";

    /**
     * Get a properly-formatted value for an "Authorization"
     * header with the scheme "Aero-Delegated-User-Device".
     *
     * @param service service making the request
     * @param deploymentSecret AeroFS shared deployment secret
     * @param user user on whose behalf the service is making the request
     * @param device device on whose behalf the service is making the request
     * @return a valid value for the "Authorization" header with the scheme "Aero-Delegated-User-Device"
     */
    public static String getHeaderValue(String service, String deploymentSecret, String user, String device) {
        return String.format(AERO_DELEGATED_USER_DEVICE_HEADER_PRINTF_FORMAT_STRING, service, deploymentSecret, BaseEncoding.base64().encode(user.getBytes(Charsets.UTF_8)), device);
    }

    private AeroDelegatedUserDevice() {
        // to prevent instantiation by subclasses
    }
}
