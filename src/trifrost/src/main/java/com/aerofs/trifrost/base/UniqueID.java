package com.aerofs.trifrost.base;

import java.security.SecureRandom;
import java.util.Date;
import java.util.UUID;

import static java.util.UUID.randomUUID;

public class UniqueID implements UniqueIDGenerator {
    public static long getDefaultRefreshExpiry() { return new Date().getTime() + 365L * 86400 * 1000; }
    public static long getDefaultTokenExpiry() { return new Date().getTime() + 365L * 86400 * 1000; }

    @Override
    public char[] generateOneTimeCode() {
        return generateString(6);
    }

    @Override
    public char[] generateDeviceString() {
        return generateString(10);
    }

    public static char[] generateUUID() {
        return toCharArray(generateImpl());
    }

    /** Return given byte array as a hex string */
    public static char[] toCharArray(byte[] bs) {
        char[] ch = new char[bs.length * 2];

        for (int i = 0; i < bs.length; ++i) {
            int v = bs[i] & 0xFF;
            ch[(i * 2) ]    = hexDigits[v >>> 4];
            ch[(i * 2) + 1] = hexDigits[v & 0x0F];
        }
        return ch;
    }

    /**
     * See RFC 4122
     */
    private static byte[] generateImpl() {
        UUID uuid = randomUUID();

        long v = uuid.getLeastSignificantBits();
        byte [] bs = new byte[LENGTH];
        for (int i = 0; i < 8; i++) {
            bs[LENGTH - 1 - i] = (byte)(v >>> (i * 8));
        }

        v = uuid.getMostSignificantBits();
        for (int i = 0; i < 8; i++) {
            bs[LENGTH - 8 - 1 - i] = (byte)(v >>> (i * 8));
        }
        return bs;
    }

    private static char[] generateString(int length) {
        char[] retval = new char[length];
        byte[] randomBytes = new byte[length];
        numberGenerator.nextBytes(randomBytes);

        for (int i=0; i<length; i++) {
            // i'll just take the bottom 4 bits rather than call Math.abs...
            retval[i] = decDigits[(randomBytes[i] & 0xF) % 10];
        }
        return retval;
    }

    static final SecureRandom numberGenerator = new SecureRandom();
    private static final char[] hexDigits = {'0', '1', '2','3','4','5','6','7','8','9','a','b','c','d','e','f'};
    private static final char[] decDigits = {'0', '1', '2','3','4','5','6','7','8','9'};
    private static final int LENGTH = 16;
}
