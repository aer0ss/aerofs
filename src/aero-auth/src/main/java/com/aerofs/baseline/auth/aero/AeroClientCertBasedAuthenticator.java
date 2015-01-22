package com.aerofs.baseline.auth.aero;

import com.aerofs.baseline.auth.AuthenticationException;
import com.aerofs.baseline.auth.AuthenticationResult;
import com.aerofs.baseline.auth.Authenticator;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.SecurityContext;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AeroClientCertBasedAuthenticator implements Authenticator {

    private static final String HASH_FUNCTION = "SHA-256";

    private static final char[] ALPHABET = {
            'a', 'b', 'c', 'd',
            'e', 'f', 'g', 'h',
            'i', 'j', 'k', 'l',
            'm', 'n', 'o', 'p',
    };

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
                            String expectedCName = getCertificateCName(aeroDevice, aeroUserid);
                            if (cname.equals(expectedCName)) {
                                return new AuthenticationResult(AuthenticationResult.Status.SUCCEEDED, new AeroSecurityContext(aeroUserid, aeroDevice, cname, Roles.CLIENT, SecurityContext.CLIENT_CERT_AUTH));
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

    public static String getCertificateCName(String did, String user) {
        return alphabetEncode(hash(user.getBytes(Charsets.UTF_8), BaseEncoding.base16().lowerCase().decode(did)));
    }

    private static String alphabetEncode(byte[] bs) {
        StringBuilder sb = new StringBuilder();

        for (byte b : bs) {
            int hi = (b >> 4) & 0xF;
            int lo = b & 0xF;
            sb.append(ALPHABET[hi]);
            sb.append(ALPHABET[lo]);
        }

        return sb.toString();
    }

    public static byte[] hash(byte[] ... blocks) {
        try {
            MessageDigest instance = MessageDigest.getInstance(HASH_FUNCTION);

            for (byte[] block : blocks) {
                instance.update(block);
            }

            return instance.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityException("cannot initialize hash function " + HASH_FUNCTION);
        }
    }
}
