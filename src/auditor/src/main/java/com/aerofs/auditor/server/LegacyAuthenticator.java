package com.aerofs.auditor.server;

import com.aerofs.baseline.auth.AuthenticationException;
import com.aerofs.baseline.auth.AuthenticationResult;
import com.aerofs.baseline.auth.Authenticator;
import com.aerofs.baseline.auth.aero.AeroAuthHeaders;
import com.aerofs.baseline.auth.aero.AeroSecurityContext;
import com.aerofs.baseline.auth.aero.Roles;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.SecurityContext;
import java.util.List;

/**
 * Implementation of the first authentication scheme
 * created for REST services. Auditor-specific, and
 * replaced by {@link com.aerofs.baseline.auth.aero.AeroClientCertBasedAuthenticator}.
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

        String user = AeroAuthHeaders.getSingleHeaderValue(HEADER_AUTH_USER, headers);
        String device = AeroAuthHeaders.getSingleHeaderValue(HEADER_AUTH_DEVICE, headers);
        String dname = AeroAuthHeaders.getSingleHeaderValue(AeroAuthHeaders.DNAME_HEADER, headers);
        String verify = AeroAuthHeaders.getSingleHeaderValue(AeroAuthHeaders.VERIFY_HEADER, headers);

        if (verify.equals(AeroAuthHeaders.VERIFY_HEADER_OK_VALUE)) {
            String cname = getCName(dname);
            if (cname.equals(AeroAuthHeaders.getCertificateCName(user, device))) {
                return new AuthenticationResult(AuthenticationResult.Status.SUCCEEDED, new AeroSecurityContext(user, device, Roles.USER, SecurityContext.CLIENT_CERT_AUTH));
            }
        }

        return AuthenticationResult.FAILED;
    }

    private static String getCName(String dname) {
        String cname = null;

        String[] dnameTokens = dname.split(AeroAuthHeaders.DNAME_SEPARATOR);
        for (String dnameToken : dnameTokens) {
            if (dnameToken.startsWith(AeroAuthHeaders.CNAME_TAG)) {
                String substring = dnameToken.substring(AeroAuthHeaders.CNAME_TAG.length());
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
