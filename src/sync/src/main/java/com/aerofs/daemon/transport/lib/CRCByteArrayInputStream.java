package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.lib.exception.ExBadCRC;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.zip.CRC32;

public class CRCByteArrayInputStream extends ByteArrayInputStream {
    public CRCByteArrayInputStream(byte[] buffer) throws ExBadCRC, IOException
    {
        this(buffer, 0, buffer.length);
    }

    public CRCByteArrayInputStream(byte[] buffer, int offset, int length) throws ExBadCRC, IOException
    {
        super(buffer, offset, length - CHECK_VALUE_LENGTH);
        verify(buffer, offset, length);
    }

    private void verify(byte[] buffer, int offset, int length) throws ExBadCRC, IOException
    {
        CRC32 crc32 = new CRC32();
        crc32.update(buffer, offset, length - CHECK_VALUE_LENGTH);
        int cv = (int) crc32.getValue();

        DataInputStream dis = new DataInputStream(
                new ByteArrayInputStream(
                        buffer,
                        length - CHECK_VALUE_LENGTH,
                        CHECK_VALUE_LENGTH));
        int storedCv = dis.readInt();

        if (cv != storedCv) throw new ExBadCRC();
    }

    private static int CHECK_VALUE_LENGTH = 4;
}
