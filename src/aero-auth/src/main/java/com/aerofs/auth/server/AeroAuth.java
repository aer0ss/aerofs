package com.aerofs.auth.server;

import com.aerofs.baseline.auth.AuthenticationException;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

/**
 * Headers, constants and functions shared across
 * AeroFS-designed HTTP authentication schemes.
 */
public abstract class AeroAuth {

    /**
     * Convenience method that returns the value of {@code header}.
     * <br>
     * This methods assumes that {@code header} has <strong>one and only one</strong>
     * value. It is considered an error if {@code header} is missing or has multiple values.
     * This method is <strong>case-insensitive</strong>.
     *
     * @param header HTTP header for which a value should be returned
     * @param headers HTTP headers associated with the incoming request
     * @return value for the specified {@code header}
     * @throws AuthenticationException if {@code header} does not exist or has multiple values
     */
    public static String getSingleAuthHeaderValue(String header, MultivaluedMap<String, String> headers) throws AuthenticationException {
        List<String> values = headers.get(header);

        if (values == null || values.size() != 1) {
            throw new AuthenticationException(header + " missing or invalid");
        }

        return values.get(0);
    }

    private AeroAuth() {
        // to prevent instantiation by subclasses
    }
}
