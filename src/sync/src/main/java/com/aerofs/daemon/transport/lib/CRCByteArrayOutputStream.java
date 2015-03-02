package com.aerofs.daemon.transport.lib;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;

public class CRCByteArrayOutputStream extends ByteArrayOutputStream {
    private CRC32 _crc;
    private boolean _cvComputed;

    public CRCByteArrayOutputStream()
    {
        _crc = new CRC32();
        _cvComputed = false;
    }

    @Override
    public synchronized byte[] toByteArray()
    {
        assert !_cvComputed;

        _crc.update(buf, 0, count);
        long cv = _crc.getValue();

        try {
            DataOutputStream ds = new DataOutputStream(this);

            ds.writeInt((int) cv);
            ds.flush();

            _cvComputed = true;
        } catch (IOException e) {
            // shouldn't happen
            assert false;
        }

        return super.toByteArray();
    }

    public long getCRC()
    {
        assert _cvComputed;
        return _crc.getValue();
    }
}
