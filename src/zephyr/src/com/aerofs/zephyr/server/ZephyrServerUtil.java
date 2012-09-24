/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.zephyr.server;

import java.nio.ByteBuffer;

import static com.aerofs.lib.Util.crc32;
import static com.aerofs.zephyr.Constants.ZEPHYR_MAGIC;
import static com.aerofs.zephyr.Constants.ZEPHYR_MSG_BYTE_ORDER;

public class ZephyrServerUtil
{
    /**
     * Populates a {@link java.nio.ByteBuffer} with a Registration message
     * <strong>does not clear() or flip() the ByteBuffer prior to, or after use</strong>
     *
     * @param b ByteBuffer to populate
     * @param id connection id
     * @return the same ByteBuffer passed in
     */
    static ByteBuffer createRegistrationMessage(ByteBuffer b, int id)
    {
        b.order(ZEPHYR_MSG_BYTE_ORDER);

        b.put(ZEPHYR_MAGIC); // magic number id'ing zephyr messages
        b.putInt(0); // length placeholder
        int lenpos = b.position(); // index after the length
        b.putInt(id); // server-assigned connection id

        int len = b.position(); // find total bytebuffer size
        b.position(ZEPHYR_MAGIC.length); // go to the spot right after the magic
        b.putInt(len - lenpos); // write the msg-len
        b.position(len); // go back to the end
        return b;
    }

    static String crc(ByteBuffer buf)
    {
        int startpos = buf.position();
        try {
            byte[] contents = new byte[buf.remaining()];
            return crc32(contents);
        } finally {
            buf.position(startpos);
        }
    }

    static void copy(ByteBuffer src, ByteBuffer dst)
    {
        int startpos = src.position();

        try {
            dst.clear();
            assert dst.remaining() >= src.remaining() : ("space exp:" + src.remaining() + " act:" + dst.remaining());
            dst.put(src);
            dst.flip();
        } finally {
            src.position(startpos);
        }
    }
}
