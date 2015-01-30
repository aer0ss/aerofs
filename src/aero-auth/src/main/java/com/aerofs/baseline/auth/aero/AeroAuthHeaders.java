package com.aerofs.baseline.auth.aero;

import com.aerofs.baseline.auth.AuthenticationException;
import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;

import javax.ws.rs.core.MultivaluedMap;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public abstract class AeroAuthHeaders {

    /**
     * Header added by nginx to indicate if the
     * certificate was signed by a recognized CA.
     */
    public static final String VERIFY_HEADER = "Verify";

    /**
     * Value of {@link AeroAuthHeaders#VERIFY_HEADER} when the
     * certificate was signed by a recognized CA.
     */
    public final static String VERIFY_HEADER_OK_VALUE = "SUCCESS";

    /**
     * Header inserted by AeroFS clients to communicate their DID and user id.
     */
    public static final String AERO_AUTHORIZATION_HEADER = "Authorization";

    /**
     * Pattern the value the {@link AeroAuthHeaders#AERO_AUTHORIZATION_HEADER}
     * should take.
     */
    public static final String AERO_AUTHORIZATION_HEADER_PATTERN = "Aero-Device-Cert ([0-9a-fA-F]{32}) (.*)";

    /**
     * Convenience printf-style format string used to write
     * the value of an {@link AeroAuthHeaders#AERO_AUTHORIZATION_HEADER}.
     */
    public static final String AERO_AUTHORIZATION_HEADER_FORMAT = "Aero-Device-Cert %s %s";

    /**
     * Header added by nginx to hold the distinguished
     * name of the cert with which the connection was secured.
     */
    public static final String DNAME_HEADER = "DName";
    public static final String DNAME_SEPARATOR = "/";
    public static final String CNAME_TAG = "CN=";

    private static final String HASH_FUNCTION = "SHA-256";

    private static final char[] ALPHABET = {
            'a', 'b', 'c', 'd',
            'e', 'f', 'g', 'h',
            'i', 'j', 'k', 'l',
            'm', 'n', 'o', 'p',
    };

    public static String getCertificateCName(String user, String did) {
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

    private static byte[] hash(byte[] ... blocks) {
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

    public static String getSingleHeaderValue(String header, MultivaluedMap<String, String> headers) throws AuthenticationException {
        List<String> values = headers.get(header);

        if (values == null || values.size() != 1) {
            throw new AuthenticationException(header + " missing or invalid");
        }

        return values.get(0);
    }

    private AeroAuthHeaders() {
        // to prevent instantiation by subclasses
    }
}
