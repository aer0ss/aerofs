package com.aerofs.baseline.auth.aero;

import com.aerofs.baseline.auth.AuthenticationException;
import com.aerofs.baseline.auth.AuthenticationResult;
import com.aerofs.baseline.auth.Authenticator;
import com.google.common.base.Strings;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.SecurityContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AeroClientCertBasedAuthenticator implements Authenticator {

    private static final Pattern AERO_AUTHORIZATION_PATTERN = Pattern.compile(AeroAuthHeaders.AERO_AUTHORIZATION_HEADER_PATTERN);

    @Override
    public String getName() {
        return "aero-client-cert";
    }

    @Override
    public AuthenticationResult authenticate(MultivaluedMap<String, String> headers) throws AuthenticationException {
        String dnameValue = headers.getFirst(AeroAuthHeaders.DNAME_HEADER);
        String verifyValue = headers.getFirst(AeroAuthHeaders.VERIFY_HEADER);
        String authorizationValue = headers.getFirst(AeroAuthHeaders.AERO_AUTHORIZATION_HEADER);

        if (!hasAuthFields(dnameValue, verifyValue, authorizationValue)) {
            return AuthenticationResult.UNSUPPORTED;
        }

        if (verifyValue.equals(AeroAuthHeaders.VERIFY_HEADER_OK_VALUE)) {
            Matcher matcher = AERO_AUTHORIZATION_PATTERN.matcher(authorizationValue);
            if (matcher.matches()) {
                String aeroDevice = matcher.group(1);
                String aeroUserid = matcher.group(2);
                String[] dnameTokens = dnameValue.split(AeroAuthHeaders.DNAME_SEPARATOR);
                for (String dnameToken : dnameTokens) {
                    if (dnameToken.startsWith(AeroAuthHeaders.CNAME_TAG)) {
                        String cname = dnameToken.substring(AeroAuthHeaders.CNAME_TAG.length());
                        if (!cname.isEmpty()) {
                            String expectedCName = AeroAuthHeaders.getCertificateCName(aeroUserid, aeroDevice);
                            if (cname.equals(expectedCName)) {
                                return new AuthenticationResult(AuthenticationResult.Status.SUCCEEDED, new AeroSecurityContext(aeroUserid, aeroDevice, Roles.USER, SecurityContext.CLIENT_CERT_AUTH));
                            }
                        }
                    }
                }
            }
        }

        return AuthenticationResult.FAILED;
    }

    private static boolean hasAuthFields(String dnameHeaderValue, String verifyHeaderValue, String authorizationValue) {
        return !Strings.isNullOrEmpty(dnameHeaderValue) && !Strings.isNullOrEmpty(verifyHeaderValue) && !Strings.isNullOrEmpty(authorizationValue);
    }
}
