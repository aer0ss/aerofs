package com.aerofs.auth.server.cert;

import com.aerofs.auth.server.AeroAuth;
import com.aerofs.auth.server.AeroSecurityContext;
import com.aerofs.auth.server.Roles;
import com.aerofs.baseline.auth.AuthenticationException;
import com.aerofs.baseline.auth.AuthenticationResult;
import com.aerofs.baseline.auth.Authenticator;
import com.aerofs.ids.DID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.UserID;
import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;
import com.google.common.net.HttpHeaders;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.aerofs.auth.server.cert.AeroDeviceCert.CNAME_TAG;
import static com.aerofs.auth.server.cert.AeroDeviceCert.DNAME_SEPARATOR;

/**
 * {@code Authenticator} implementation that constructs
 * {@code SecurityContext} instances for requests that use the
 * "Aero-Device-Cert" authentication scheme.
 * <br>
 * This implementation throws {@link AuthenticationException} if
 * the authentication scheme is identified as "Aero-Device-Cert"
 * and the payload has too few/too many parameters or the parameters
 * are invalid.
 * <br>
 * This implementation returns {@link AuthenticationResult#FAILED}
 * if the authentication scheme is identified as "Aero-Device-Cert"
 * but the user/device encoded in the certificate CName does not
 * match the values the device self-reports.
 */
public final class AeroDeviceCertAuthenticator implements Authenticator {

    private static final Pattern COMPILED_REGEX = Pattern.compile(AeroDeviceCert.AERO_DEVICE_CERT_HEADER_REGEX);

    @Override
    public String getName() {
        return AeroDeviceCert.AUTHENTICATION_SCHEME;
    }

    @Override
    public AuthenticationResult authenticate(MultivaluedMap<String, String> headers) throws AuthenticationException {
        List<String> authHeaders = headers.get(HttpHeaders.AUTHORIZATION);

        // does it even have our auth header?
        if (authHeaders == null || authHeaders.size() == 0) {
            return AuthenticationResult.UNSUPPORTED;
        }

        // check that we only have one instance of that header
        if (authHeaders.size() > 1) {
            throw new AuthenticationException("multiple Authorization headers");
        }

        // it does - check if it's the "Aero-Device-Cert" one
        String authValue = authHeaders.get(0);
        if (!authValue.startsWith(AeroDeviceCert.AUTHENTICATION_SCHEME)) {
            return AuthenticationResult.UNSUPPORTED;
        }

        // check if the authentication scheme format is correct
        Matcher matcher = COMPILED_REGEX.matcher(authValue);
        if (!matcher.matches()) {
            throw new AuthenticationException("invalid authentication scheme format"); // explicitly don't specify what's broken
        }

        // it matches - parse it out, and get the other header values
        String encodedUserValue = matcher.group(1);
        String deviceValue = matcher.group(2);
        String verifyValue = AeroAuth.getSingleAuthHeaderValue(AeroDeviceCert.AERO_VERIFY_HEADER, headers);
        String dnameValue = AeroAuth.getSingleAuthHeaderValue(AeroDeviceCert.AERO_DNAME_HEADER, headers);

        // did nginx verify the cert against the CA?
        if (verifyValue.equals(AeroDeviceCert.AERO_VERIFY_SUCCEEDED_HEADER_VALUE)) {
            String reportedCName = null;

            // try grab the CName component from the DName header
            int idx = dnameValue.indexOf(CNAME_TAG);
            if (idx != -1) {
                idx += CNAME_TAG.length();
                int end = dnameValue.indexOf(DNAME_SEPARATOR, idx);
                reportedCName = dnameValue.substring(idx, end != -1 ? end : dnameValue.length());
            }

            // if the cname doesn't exist the dname value is broken
            if (reportedCName == null) {
                throw new AuthenticationException(AeroDeviceCert.AERO_DNAME_HEADER + " has invalid format (" + dnameValue + ")");
            }

            // let's see if the device-reported id/device match the one in its cert
            try {
                String userValue = new String(BaseEncoding.base64().decode(encodedUserValue), Charsets.UTF_8);
                String expectedCName = AeroDeviceCert.getCertificateCName(userValue, deviceValue);

                UserID user = UserID.fromInternalThrowIfNotNormalized(userValue);
                DID device = new DID(deviceValue);

                if (expectedCName.equals(reportedCName)) {
                    return new AuthenticationResult(AuthenticationResult.Status.SUCCEEDED, new AeroSecurityContext(new AeroDeviceCertPrincipal(user, device), Roles.USER, AeroDeviceCert.AUTHENTICATION_SCHEME));
                }
            } catch (IllegalArgumentException|ExInvalidID e) { // base64 decoding failed
                throw new AuthenticationException("invalid authentication scheme format");
            }
        }

        return AuthenticationResult.FAILED;
    }
}
