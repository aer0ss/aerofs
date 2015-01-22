package com.aerofs.baseline.auth.aero;

public abstract class AeroAuthHeaders {

    /**
     * Header added by nginx to indicate if the
     * certificate was signed by a recognized CA.
     */
    public static final String VERIFY_HEADER = "Verify";

    /**
     * Value of {@link AeroAuthHeaders#VERIFY_HEADER} when the
     * certificate was signed by a recognized CA.
     */
    public final static String VERIFY_HEADER_OK_VALUE = "SUCCESS";

    /**
     * Header inserted by AeroFS clients to communicate their DID and user id.
     */
    public static final String AERO_AUTHORIZATION_HEADER = "Authorization";

    /**
     * Pattern the value the {@link AeroAuthHeaders#AERO_AUTHORIZATION_HEADER}
     * should take.
     */
    public static final String AERO_AUTHORIZATION_HEADER_PATTERN = "Aero-Device-Cert ([0-9a-fA-F]{32}) (.*)";

    /**
     * Convenience printf-style format string used to write
     * the value of an {@link AeroAuthHeaders#AERO_AUTHORIZATION_HEADER}.
     */
    public static final String AERO_AUTHORIZATION_HEADER_FORMAT = "Aero-Device-Cert %s %s";

    /**
     * Header added by nginx to hold the distinguished
     * name of the cert with which the connection was secured.
     */
    public static final String DNAME_HEADER = "DName";
    public static final String DNAME_SEPARATOR = "/";
    public static final String CNAME_TAG = "CN=";

    private AeroAuthHeaders() {
        // to prevent instantiation by subclasses
    }
}
