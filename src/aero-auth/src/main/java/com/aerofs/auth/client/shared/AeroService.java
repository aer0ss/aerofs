package com.aerofs.auth.client.shared;

/**
 * Client-side convenience headers, constants and
 * functions specific to the "Aero-Service-Shared-Secret"
 * HTTP authentication scheme.
 */
public abstract class AeroService {

    private static final String AERO_SERVICE_SHARED_SECRET_HEADER_PRINTF_FORMAT_STRING = "Aero-Service-Shared-Secret %s %s";

    /**
     * Get a properly-formatted value for an "Authorization"
     * header with the scheme "Aero-Service-Shared-Secret".
     * <br>
     * This method <strong>does not</strong> validate
     * the values or format of the user-specified inputs.
     *
     * @param service name of the service making the request
     * @param deploymentSecret value of the deployment secret shared by all services in this installation
     * @return a valid value for the "Authorization" header with the scheme "Aero-Service-Shared-Secret"
     */
    public static String getHeaderValue(String service, String deploymentSecret) {
        return String.format(AERO_SERVICE_SHARED_SECRET_HEADER_PRINTF_FORMAT_STRING, service, deploymentSecret);
    }

    private AeroService() {
        // to prevent instantiation by subclasses
    }
}
