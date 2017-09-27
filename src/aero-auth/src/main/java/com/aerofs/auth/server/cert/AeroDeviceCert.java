package com.aerofs.auth.server.cert;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Server-side headers, constants and functions specific
 * to the "Aero-Device-Cert" HTTP authentication scheme.
 */
public abstract class AeroDeviceCert {

    /**
     * Name assigned to this authentication scheme.
     * This is also the scheme name used in the "Authorization" header.
     */
    public static final String AUTHENTICATION_SCHEME = "Aero-Device-Cert";

    /**
     * Header added by nginx to indicate if the
     * certificate was signed by a recognized CA.
     */
    public static final String AERO_VERIFY_HEADER = "Verify";

    /**
     * Value of {@link #AERO_VERIFY_HEADER} when the
     * certificate was signed by a recognized CA.
     */
    public final static String AERO_VERIFY_SUCCEEDED_HEADER_VALUE = "SUCCESS";

    /**
     * Regex pattern the "Authorization" header value should have
     * for the "Aero-Device-Cert" authentication scheme.
     */
    public static final String AERO_DEVICE_CERT_HEADER_REGEX = "Aero-Device-Cert ([a-zA-Z0-9=\\+\\/]+) ([0-9a-fA-F]{32})";

    /**
     * Header added by nginx to hold the distinguished
     * name of the cert with which the connection was secured.
     */
    public static final String AERO_DNAME_HEADER = "DName";

    /**
     * Separator between various components of the {@link #AERO_DNAME_HEADER}.
     */
    public static final String DNAME_SEPARATOR = ",";

    /**
     * Tag identifying the Common-Name component in the {@link #AERO_DNAME_HEADER}.
     */
    public static final String CNAME_TAG = "CN=";

    /**
     * Generate an AeroFS-style certificate CName for
     * a given {@code user}, {@code device} pair.
     * <br>
     * This CName will be included in the {@link #AERO_DNAME_HEADER}
     * necessary for "Aero-Device-Cert" authentication.
     *
     * @param user user id of the user for whom the CName will be generated
     * @param device 32-character hex DID of the device for which the CName will be generated
     * @return valid CName that both encodes {@code user}, {@code device}
     * and that can be included as part of {@link #AERO_DNAME_HEADER}
     */
    public static String getCertificateCName(String user, String device) {
        return alphabetEncode(hash(user.getBytes(Charsets.UTF_8), BaseEncoding.base16().lowerCase().decode(device)));
    }

    private static final char[] ALPHABET = {
            'a', 'b', 'c', 'd',
            'e', 'f', 'g', 'h',
            'i', 'j', 'k', 'l',
            'm', 'n', 'o', 'p',
    };

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

    private static final String HASH_FUNCTION = "SHA-256";

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

    private AeroDeviceCert() {
        // to prevent instantiation by subclasses
    }
}
