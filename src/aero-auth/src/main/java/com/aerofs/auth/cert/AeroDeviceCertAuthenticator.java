package com.aerofs.auth.cert;

import com.aerofs.auth.AeroSecurityContext;
import com.aerofs.auth.Roles;
import com.aerofs.baseline.auth.AuthenticationException;
import com.aerofs.baseline.auth.AuthenticationResult;
import com.aerofs.baseline.auth.Authenticator;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.SecurityContext;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AeroDeviceCertAuthenticator implements Authenticator {

    private static final Pattern COMPILED_DEVICE_CERT_HEADER_PATTERN = Pattern.compile(AeroDeviceCert.AERO_DEVICE_CERT_HEADER_PATTERN);

    @Override
    public String getName() {
        return "Aero-Device-Cert";
    }

    @Override
    public AuthenticationResult authenticate(MultivaluedMap<String, String> headers) throws AuthenticationException {
        List<String> authHeaders = headers.get(AeroDeviceCert.AERO_AUTHORIZATION_HEADER);

        // does it even have our auth header?
        if (authHeaders == null || authHeaders.size() == 0) {
            return AuthenticationResult.UNSUPPORTED;
        }

        // it does - check if it's the "Aero-Device-Cert" one
        String auth = AeroDeviceCert.getSingleHeaderValue(AeroDeviceCert.AERO_AUTHORIZATION_HEADER, headers);
        Matcher matcher = COMPILED_DEVICE_CERT_HEADER_PATTERN.matcher(auth);
        if (!matcher.matches()) {
            return AuthenticationResult.UNSUPPORTED;
        }

        // it matches - parse it out, and get the other header values
        String aeroDevice = matcher.group(1);
        String aeroUserid = matcher.group(2);
        String dname = AeroDeviceCert.getSingleHeaderValue(AeroDeviceCert.AERO_DNAME_HEADER, headers);
        String verify = AeroDeviceCert.getSingleHeaderValue(AeroDeviceCert.AERO_VERIFY_HEADER, headers);

        // did nginx verify the cert against the CA?
        if (verify.equals(AeroDeviceCert.AERO_VERIFY_HEADER_OK_VALUE)) {
            String cname = null;

            // try grab the CName component from the DName header
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

            // if the cname doesn't exist the dname value is broken
            // otherwise, let's see if the user-reported id/device match the one for the cert
            if (cname != null) {
                String expectedCName = AeroDeviceCert.getCertificateCName(aeroUserid, aeroDevice);
                if (cname.equals(expectedCName)) {
                    return new AuthenticationResult(AuthenticationResult.Status.SUCCEEDED, new AeroSecurityContext(aeroUserid, aeroDevice, Roles.USER, SecurityContext.CLIENT_CERT_AUTH));
                }
            } else {
                throw new AuthenticationException(AeroDeviceCert.AERO_DNAME_HEADER + " has invalid format (" + dname + ")");
            }
        }

        return AuthenticationResult.FAILED;
    }
}
