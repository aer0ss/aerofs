package com.aerofs.auditor.server;

import com.aerofs.baseline.auth.AuthenticationException;
import com.aerofs.baseline.auth.AuthenticationResult;
import com.aerofs.baseline.auth.Authenticator;
import com.aerofs.auth.cert.AeroDeviceCert;
import com.aerofs.auth.AeroSecurityContext;
import com.aerofs.auth.Roles;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.SecurityContext;
import java.util.List;

/**
 * Implementation of the first authentication scheme
 * created for REST services. Auditor-specific, and
 * replaced by {@link com.aerofs.auth.cert.AeroDeviceCertAuthenticator}.
 */
public class LegacyAuthenticator implements Authenticator {

    private static final String HEADER_AUTH_REQ = "AeroFS-Auth-Required";
    private static final String HEADER_AUTH_USER = "AeroFS-UserId";
    private static final String HEADER_AUTH_DEVICE = "AeroFS-DeviceId";

    @Override
    public String getName() {
        return "aero-client-cert";
    }

    @Override
    public AuthenticationResult authenticate(MultivaluedMap<String, String> headers) throws AuthenticationException {
        List<String> authHeaders = headers.get(HEADER_AUTH_REQ);

        if (authHeaders == null || authHeaders.size() == 0) {
            // if no headers are present we assume
            // that it's a backend service (namely SP)
            // that's talking to us
            // this is a dangerous hack
            return new AuthenticationResult(AuthenticationResult.Status.SUCCEEDED, new AeroSecurityContext("NONE", "NONE", Roles.SERVICE, "NONE"));
        } else if (authHeaders.size() > 1) {
            throw new AuthenticationException("invalid value for hdr:" + HEADER_AUTH_REQ);
        }

        boolean authRequired = Boolean.parseBoolean(authHeaders.get(0));
        if (!authRequired) { // Why would we put a header in and then not ask for verification?
            return AuthenticationResult.UNSUPPORTED;
        }

        String user = AeroDeviceCert.getSingleHeaderValue(HEADER_AUTH_USER, headers);
        String device = AeroDeviceCert.getSingleHeaderValue(HEADER_AUTH_DEVICE, headers);
        String dname = AeroDeviceCert.getSingleHeaderValue(AeroDeviceCert.AERO_DNAME_HEADER, headers);
        String verify = AeroDeviceCert.getSingleHeaderValue(AeroDeviceCert.AERO_VERIFY_HEADER, headers);

        if (verify.equals(AeroDeviceCert.AERO_VERIFY_HEADER_OK_VALUE)) {
            String cname = getCName(dname);
            if (cname.equals(AeroDeviceCert.getCertificateCName(user, device))) {
                return new AuthenticationResult(AuthenticationResult.Status.SUCCEEDED, new AeroSecurityContext(user, device, Roles.USER, SecurityContext.CLIENT_CERT_AUTH));
            }
        }

        return AuthenticationResult.FAILED;
    }

    private static String getCName(String dname) {
        String cname = null;

        String[] dnameTokens = dname.split(AeroDeviceCert.DNAME_SEPARATOR);
        for (String dnameToken : dnameTokens) {
            if (dnameToken.startsWith(AeroDeviceCert.CNAME_TAG)) {
                String substring = dnameToken.substring(AeroDeviceCert.CNAME_TAG.length());
                if (!substring.isEmpty()) {
                    cname = substring;
                    break;
                }
            }
        }

        if (cname == null) {
            throw new AuthenticationException("invalid dname format dname:" + dname);
        }

        return cname;
    }
}
