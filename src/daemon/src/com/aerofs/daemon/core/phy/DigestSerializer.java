package com.aerofs.daemon.core.phy;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.MessageDigestSpi;
import java.security.NoSuchAlgorithmException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Reflection-based (de)serialization of MessageDigest
 *
 * This approach is only viable because we ship our own JVM and can therefore rely on
 * the exact shape of the internal classes used to implement the digest of interest.
 *
 * At this time, only SHA-256 is supported because it is the only one that the daemon
 * uses in a context where incremental hashing is useful.
 *
 * See also:
 * {@link sun.security.provider.DigestBase}
 * {@link sun.security.provider.SHA2}
 */
public final class DigestSerializer
{
    public static final int SERIALIZED_SIZE
            = 8             // bytesProcessed
            + 4             // bufOfs
            + 64            // buffer (SHA 256 block size)
            + 32;           // state

    public static byte[] serialize(MessageDigest md)
    {
        checkState(md.getAlgorithm().equals("SHA-256"));
        ByteBuffer b = ByteBuffer.allocate(SERIALIZED_SIZE);
        try {
            MessageDigestSpi spi = (MessageDigestSpi) field(md, "digestSpi").get(md);

            // buffered input (DigestBase)
            b.putLong(field(spi, "bytesProcessed").getLong(spi));
            b.putInt(field(spi, "bufOfs").getInt(spi));
            byte[] buffer = (byte[]) field(spi, "buffer").get(spi);
            checkState(buffer.length == 64, "unexpected buffer length: %s", buffer.length);
            b.put(buffer);

            // SHA-256 state
            int[] state = (int[]) field(spi, "state").get(spi);
            checkState(state.length == 8, "unexpected state length: %s", state.length);
            for (int i = 0; i < state.length; ++i) {
                b.putInt(state[i]);
            }
        } catch (NoSuchFieldException|IllegalAccessException e) {
            throw new IllegalArgumentException("unsupported digest", e);
        }
        return b.array();
    }

    public static MessageDigest deserialize(byte[] d)
    {
        return deserialize(d, -1);
    }

    public static MessageDigest deserialize(byte[] d, long expectedBytesProcessed)
    {
        checkState(d.length == SERIALIZED_SIZE, "Invalid input size: %s", d.length);
        ByteBuffer b = ByteBuffer.wrap(d);
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e);
        }
        try {
            MessageDigestSpi spi = (MessageDigestSpi) field(md, "digestSpi").get(md);

            long bytesProcessed = b.getLong();
            if (expectedBytesProcessed >= 0) {
                checkArgument(expectedBytesProcessed == bytesProcessed,
                        "Mismatching number of processed bytes: %s != %s",
                        bytesProcessed, expectedBytesProcessed);
            }
            field(spi, "bytesProcessed").setLong(spi, bytesProcessed);
            field(spi, "bufOfs").setInt(spi, b.getInt());
            byte[] buffer = new byte[64];
            b.get(buffer);
            field(spi, "buffer").set(spi, buffer);

            int[] state = new int[8];
            for (int i = 0; i < state.length; ++i) {
                state[i] = b.getInt();
            }
            field(spi, "state").set(spi, state);
        } catch (NoSuchFieldException|IllegalAccessException e) {
            throw new IllegalArgumentException("unsupported digest", e);
        }
        return md;
    }

    private static Field field(Object o, String n) throws NoSuchFieldException
    {
        Class<?> c = o.getClass();
        while (true) {
            try {
                Field f = c.getDeclaredField(n);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
                if (c == null) throw e;
            }
        }
    }
}
