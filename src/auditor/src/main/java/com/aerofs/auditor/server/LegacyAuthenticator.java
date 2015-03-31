package com.aerofs.auditor.server;

import com.aerofs.auth.server.AeroAuth;
import com.aerofs.auth.server.AeroSecurityContext;
import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.auth.server.cert.AeroDeviceCert;
import com.aerofs.auth.server.shared.AeroServicePrincipal;
import com.aerofs.baseline.auth.AuthenticationException;
import com.aerofs.baseline.auth.AuthenticationResult;
import com.aerofs.baseline.auth.Authenticator;
import com.aerofs.ids.DID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.UserID;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

/**
 * Implementation of the first authentication scheme
 * created for REST services. Auditor-specific, and
 * replaced by {@link com.aerofs.auth.server.cert.AeroDeviceCertAuthenticator}.
 */
public class LegacyAuthenticator implements Authenticator
{
    /**
     * Implementation of {@link AeroUserDevicePrincipal}
     * that represents an entity authorized using the legacy
     * Aero-Device-Cert authentication mechanism (auditor only).
     */
    private static class LegacyUserDevicePrincipal implements AeroUserDevicePrincipal
    {
        private final UserID _user;
        private final DID _device;

        private LegacyUserDevicePrincipal(UserID user, DID device)
        {
            _user = user;
            _device = device;
        }

        @Override
        public String getName()
        {
            return String.format("%s:%s", _user.getString(), _device.toStringFormal());
        }

        @Override
        public UserID getUser()
        {
            return _user;
        }

        @Override
        public DID getDevice()
        {
            return _device;
        }
    }

    private static final String AUTHENTICATION_SCHEME = "Legacy-Aero-Device-Cert";
    private static final String HEADER_AUTH_REQ = "AeroFS-Auth-Required";
    private static final String HEADER_AUTH_USER = "AeroFS-UserId";
    private static final String HEADER_AUTH_DEVICE = "AeroFS-DeviceId";
    private static final String HEADER_AUTH_VERIFY = "Verify";
    private static final String HEADER_AUTH_DNAME = "DName";

    @Override
    public String getName() {
        return AUTHENTICATION_SCHEME;
    }

    @Override
    public AuthenticationResult authenticate(MultivaluedMap<String, String> headers) throws AuthenticationException {
        List<String> authHeaders = headers.get(HEADER_AUTH_REQ);

        if (authHeaders == null || authHeaders.size() == 0) {
            // If not prompted by an nginx header, don't bother attempting to allow this auth mechanism
            return AuthenticationResult.UNSUPPORTED;
        }
        if (authHeaders.size() != 1) {
            // Too many headers?  Something's up, complain loudly.
            throw new AuthenticationException("invalid value for hdr:" + HEADER_AUTH_REQ);
        }

        boolean authRequired = Boolean.parseBoolean(authHeaders.get(0));
        if (!authRequired) { // Why would we put a header in and then not ask for verification?
            return AuthenticationResult.UNSUPPORTED;
        }

        String userValue = AeroAuth.getSingleAuthHeaderValue(HEADER_AUTH_USER, headers);
        String deviceValue = AeroAuth.getSingleAuthHeaderValue(HEADER_AUTH_DEVICE, headers);
        String dnameValue = AeroAuth.getSingleAuthHeaderValue(HEADER_AUTH_DNAME, headers);
        String verifyValue = AeroAuth.getSingleAuthHeaderValue(HEADER_AUTH_VERIFY, headers);

        try {
            if (verifyValue.equals(AeroDeviceCert.AERO_VERIFY_SUCCEEDED_HEADER_VALUE)) {
                String cname = getCName(dnameValue);
                if (cname.equals(AeroDeviceCert.getCertificateCName(userValue, deviceValue))) {
                    UserID user = UserID.fromExternal(userValue);
                    DID device = new DID(deviceValue);
                    return new AuthenticationResult(AuthenticationResult.Status.SUCCEEDED, new AeroSecurityContext(new LegacyUserDevicePrincipal(user, device), Roles.USER, AUTHENTICATION_SCHEME));
                }
            }
        } catch (ExInvalidID e) {
            throw new AuthenticationException("invalid authentication scheme format");
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
