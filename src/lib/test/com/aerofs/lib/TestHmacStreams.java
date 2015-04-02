/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import com.aerofs.base.BaseSecUtil.HmacVerifyingInputStream;
import com.aerofs.base.BaseSecUtil.HmacAppendingOutputStream;
import com.aerofs.testlib.AbstractTest;
import org.junit.Test;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Random;
import static org.junit.Assert.*;

public class TestHmacStreams extends AbstractTest
{
    int TEST_MESSAGE_SIZE = 64;
    char[] _passwd = "password_goes_here".toCharArray();

    /**
     * This test verifies that a bytestring containing a message and an HMAC is longer than the
     * original bytestring.
     */
    @Test
    public void shouldAppendMac() throws IOException, GeneralSecurityException
    {
        SecretKey key = SecUtil.getAESSecretKey(_passwd, true);
        byte[] randomData = SecUtil.newRandomBytes(TEST_MESSAGE_SIZE);

        byte[] hmaced_data = hmacit(key, randomData);
        // We expect hmaced_data to be:
        // randomData | HMAC(IV, randomData)
        assertTrue("HMACed data should be longer than original data",
                hmaced_data.length > randomData.length);
    }

    /**
     * This test verifies that encoding and decoding a set of random bytes results in the original
     * bytestring.
     */
    @Test
    public void shouldReadBackSameData() throws IOException, GeneralSecurityException
    {
        // Generate key, data
        SecretKey key = SecUtil.getAESSecretKey(_passwd, true);
        byte[] randomData = SecUtil.newRandomBytes(TEST_MESSAGE_SIZE);

        // Create hmac'd message
        byte[] hmaced_message = hmacit(key, randomData);

        // Read message back from hmac'd bytestring
        ByteArrayInputStream byte_in = new ByteArrayInputStream(hmaced_message);
        InputStream in = new HmacVerifyingInputStream(key, byte_in);
        byte[] dataReadBack = new byte[randomData.length];
        int bytes_read = 0;
        int total_bytes_read = 0;
        while (bytes_read != -1 && total_bytes_read != dataReadBack.length) {
            total_bytes_read += bytes_read;
            bytes_read = in.read(dataReadBack, total_bytes_read, dataReadBack.length - total_bytes_read);
        }
        // The number of bytes that the underlying stream lets us read should be exactly
        // the number the original message contained; this tests whether the HmacInputStream
        // holds back the MAC bytes correctly.
        assert total_bytes_read == randomData.length;
        in.close();
        // Verify that the data itself matches our expectation.
        assertTrue("Data out should match data in",
                SecUtil.constantTimeIsEqual(dataReadBack, randomData));
    }

    /**
     * This test verifies that an undersized input generates an exception (because it's too short
     * to contain the HMAC at the end)
     */
    @Test(expected = IOException.class)
    public void shouldThrowIfNoHmacToRead() throws IOException, GeneralSecurityException
    {
        SecretKey key = SecUtil.getAESSecretKey(_passwd, true);
        byte[] data = {0};
        ByteArrayInputStream byte_in = new ByteArrayInputStream(data);
        // This should throw an IOException because the underlying stream has insufficient data.
        InputStream in = new HmacVerifyingInputStream(key, byte_in);
        byte[] dataReadBack = new byte[1];
        int bytes_read = 0;
        while(bytes_read != -1) {
            bytes_read = in.read(dataReadBack);
        }
        in.close();
    }

    /**
     * This test verifies that an attempt to read a modified input eventually generates an
     * exception
     */
    @Test(expected = IOException.class)
    public void shouldThrowIfDataChanged() throws IOException, GeneralSecurityException
    {
        // Generate key, data
        SecretKey key = SecUtil.getAESSecretKey(_passwd, true);
        byte[] randomData = SecUtil.newRandomBytes(TEST_MESSAGE_SIZE);

        // Create hmac'd message
        byte[] hmaced_message = hmacit(key, randomData);

        // Flip some bits in some random location in the message or checksum
        Random rng = new Random();
        int offset = rng.nextInt(hmaced_message.length);
        hmaced_message[offset] ^= 0xff;

        // Try to read message back from hmac'd bytestring
        ByteArrayInputStream byte_in = new ByteArrayInputStream(hmaced_message);
        InputStream in = new HmacVerifyingInputStream(key, byte_in);
        byte[] dataReadBack = new byte[randomData.length];
        int bytes_read = 0;
        int total_bytes_read = 0;
        while(bytes_read != -1) {
            // When the underlying stream would return -1, this should throw because the MAC
            // won't match the last bytes in the stream
            bytes_read = in.read(dataReadBack);
            total_bytes_read += bytes_read;
        }
        // The following code should never execute.
        assertEquals(total_bytes_read, randomData.length);
        in.close();
        // Verify that the data itself matches our expectation.
        assertTrue("Data in should match data out",
                SecUtil.constantTimeIsEqual(dataReadBack, randomData));
    }

    private byte[] hmacit(SecretKey key, byte[] bytes) throws IOException, GeneralSecurityException
    {
        ByteArrayOutputStream byte_out = new ByteArrayOutputStream();
        OutputStream out = new HmacAppendingOutputStream(key, byte_out);
        out.write(bytes);
        // We shouldn't write the HMAC until we've closed the file.
        byte_out.flush();
        //Assert.assertEquals(byte_out.toByteArray(), bytes, "HMAC shouldn't be written until " +
                //"stream is closed");
        out.close();
        byte[] hmaced_message = byte_out.toByteArray();
        assertFalse("HMACed message should not be identical to original",
                SecUtil.constantTimeIsEqual(hmaced_message, bytes));
        return hmaced_message;
    }
}
