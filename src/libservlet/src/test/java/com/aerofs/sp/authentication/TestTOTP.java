/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.authentication;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class TestTOTP
{
    private static class TestCase {
        TestCase(long timeInSecSinceEpoch, int digits, int expectedOutput) {
            _timeInSecSinceEpoch = timeInSecSinceEpoch;
            _outputDigits = digits;
            _expectedOutput = expectedOutput;
        }
        public long _timeInSecSinceEpoch;
        public int _outputDigits;
        public int _expectedOutput;
    }

    // Test cases from RFC6238 appendix B: http://tools.ietf.org/html/rfc6238#appendix-B
    // We only use the SHA1 implementation
    private TestCase[] _testCases = {
            new TestCase(         59L, 8, 94287082),
            new TestCase( 1111111109L, 8,  7081804),
            new TestCase( 1111111111L, 8, 14050471),
            new TestCase( 1234567890L, 8, 89005924),
            new TestCase( 2000000000L, 8, 69279037),
            new TestCase(20000000000L, 8, 65353130),
    };

    @Test
    public void shouldPassAllRfcTestVectors()
            throws Exception
    {
        for (TestCase t : _testCases) {
            // The test vectors use the 20-byte secret (ASCII) "12345678901234567890"
            byte[] secret = {
                    0x31, 0x32, 0x33, 0x34, 0x35,
                    0x36, 0x37, 0x38, 0x39, 0x30,
                    0x31, 0x32, 0x33, 0x34, 0x35,
                    0x36, 0x37, 0x38, 0x39, 0x30,
                    };
            long counter = TOTP.epochCounter(TimeUnit.SECONDS.toMillis(t._timeInSecSinceEpoch));
            assertEquals(TOTP.hotp(secret, counter, t._outputDigits),
                         t._expectedOutput);
        }
    }
}
