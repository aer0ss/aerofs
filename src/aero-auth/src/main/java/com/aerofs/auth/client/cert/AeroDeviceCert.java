package com.aerofs.auth.client.cert;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;

/**
 * Client-side convenience headers, constants and
 * functions specific to the "Aero-Device-Cert"
 * HTTP authentication scheme.
 */
public abstract class AeroDeviceCert {

    private static final String AERO_DEVICE_CERT_HEADER_PRINTF_FORMAT_STRING = "Aero-Device-Cert %s %s";

    /**
     * Get a properly-formatted value for an "Authorization"
     * header with the scheme "Aero-Device-Cert".
     * <br>
     * This method <strong>does not</strong> validate
     * the values or format of the user-specified inputs.
     *
     * @param user user making the request
     * @param device device making the request
     * @return a valid value for the "Authorization" header with the scheme "Aero-Device-Cert"
     */
    public static String getHeaderValue(String user, String device) {
        return String.format(AERO_DEVICE_CERT_HEADER_PRINTF_FORMAT_STRING, BaseEncoding.base64().encode(user.getBytes(Charsets.UTF_8)), device);
    }

    private AeroDeviceCert() {
        // to prevent instantiation by subclasses
    }
}
