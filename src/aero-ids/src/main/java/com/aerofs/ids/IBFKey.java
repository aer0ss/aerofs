package com.aerofs.ids;

import java.nio.ByteBuffer;

/**
 * Interface for any object to be used as a key to Bloom filters
 * The object must expose a byte array to be hashed: either it contains
 *  a native byte array, or will convert to a byte array.
 * @author markj
 *
 */
public interface IBFKey {
    public ByteBuffer getReadOnlyByteBuffer();
}