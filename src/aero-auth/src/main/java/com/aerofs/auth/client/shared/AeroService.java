package com.aerofs.auth.client.shared;

import com.google.common.io.ByteStreams;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.google.common.base.Preconditions.checkState;

/**
 * Client-side convenience headers, constants and
 * functions specific to the "Aero-Service-Shared-Secret"
 * HTTP authentication scheme.
 */
public abstract class AeroService {

    private static final String AERO_SERVICE_SHARED_SECRET_HEADER_PRINTF_FORMAT_STRING = "Aero-Service-Shared-Secret %s %s";

    // The path on the filesystem of the shared secret that we use to prove to other services that we are a valid client
    private static final String DEPLOYMENT_SECRET_PATH = "/data/deployment_secret";

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

    public static String loadDeploymentSecret(String path) {
        try (InputStream is = new FileInputStream(path)) {
            String s = new String(ByteStreams.toByteArray(is), StandardCharsets.UTF_8).trim();
            checkState(s.length() == 32, "Invalid deployment secret %s", s);
            return s;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load deployment secret", e);
        }
    }

    public static String loadDeploymentSecret() {
        return loadDeploymentSecret(DEPLOYMENT_SECRET_PATH);
    }

    private AeroService() {
        // to prevent instantiation by subclasses
    }
}
