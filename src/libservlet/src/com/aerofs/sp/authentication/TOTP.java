/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.authentication;

import com.google.common.base.Preconditions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * A Time-Based One-Time Password implementation, compliant with RFC 6238
 * http://tools.ietf.org/html/rfc6238
 */
public class TOTP
{
    static int TIME_STEP = 30; // Number of seconds

    /**
     * Tests if userInput matches the time-based derived secret for the current time window or
     * maxTimeStepFudge windows before or after now.
     *
     * @param secret The TOTP root shared secret
     * @param userInput The value submitted by the user
     * @param maxTimeStepFudge Number of 30-second time steps to also allow the
     * @return true if userInput is a valid TOTP code for the given secret for the current time
     *         window, or is a valid TOTP code for a window up to maxTimeStepFudge time windows in
     *         the past or future (to deal with clock drift)
     * @throws InvalidKeyException
     * @throws NoSuchAlgorithmException
     */
    public static boolean check(byte[] secret, int userInput, int maxTimeStepFudge)
            throws InvalidKeyException, NoSuchAlgorithmException
    {
        Preconditions.checkArgument(maxTimeStepFudge >= 0);
        for (int i = -maxTimeStepFudge ; i <= maxTimeStepFudge ; i++) {
            long timeStepsElapsed = new Date().getTime() / TimeUnit.SECONDS.toMillis(TIME_STEP);
            if ( hotp(secret, timeStepsElapsed + i) == userInput ) {
                return true;
            }
        }
        return false;
    }

    // Compute the current TOTP for the specified secret
    private static int totp(byte[] secret)
            throws NoSuchAlgorithmException, InvalidKeyException
    {
        // Assumes integer division truncation
        long timeStepsElapsed = epochCounter(new Date().getTime());
        System.out.println("counter: " + timeStepsElapsed);
        return hotp(secret, timeStepsElapsed);
    }

    private static int hotp(byte[] secret, long counter)
            throws InvalidKeyException, NoSuchAlgorithmException
    {
        return hotp(secret, counter, 6);
    }

    // Package-private for testing
    static long epochCounter(long timestamp)
    {
        return timestamp / TimeUnit.SECONDS.toMillis(TIME_STEP);
    }

    // Package-private for testing
    static int hotp(byte[] secret, long counter, long outputDigits)
            throws NoSuchAlgorithmException, InvalidKeyException
    {
        Preconditions.checkArgument(outputDigits >= 6 && outputDigits <= 8);
        SecretKeySpec keySpec = new SecretKeySpec(
                secret,
                "HmacSHA1");

        byte[] counterBytes = new byte[8];
        ByteBuffer bb = ByteBuffer.wrap(counterBytes);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.putLong(counter);

        // New hmac, using sha1
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(keySpec);
        mac.update(counterBytes);
        byte[] hs = mac.doFinal(); // HS per RFC4226

        int offset = hs[19] & 0x0f;
        ByteBuffer dbcbuf = ByteBuffer.wrap(hs);
        int dbc1 = dbcbuf.getInt(offset); // DT(hs)
        int dbc2 = dbc1 & 0x7fffffff;

        int modulus = 1;
        for (int i = 0 ; i < outputDigits ; i++) {
            modulus *= 10;
        }
        return dbc2 % modulus;
    }
}
