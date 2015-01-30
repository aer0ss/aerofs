package com.aerofs.daemon.core.phy.linked;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.TransUtil;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;

public class LinkedPrefix extends AbstractLinkedObject implements IPhysicalPrefix
{
    private final SOKID _sokid;

    public LinkedPrefix(LinkedStorage s, SOKID sokid, LinkedPath path)
    {
        super(s);
        _sokid = sokid;
        setPath(path);
    }

    @Override
    SOID soid()
    {
        return _sokid.soid();
    }

    @Override
    public long getLength_()
    {
        return _f.getLengthOrZeroIfNotFile();
    }

    @Override
    public OutputStream newOutputStream_(boolean append) throws IOException
    {
        return new FileOutputStream(_f.getImplementation(), append);
    }

    @Override
    public InputStream newInputStream_() throws IOException
    {
        return new FileInputStream(_f.getImplementation());
    }

    @Override
    public void moveTo_(IPhysicalPrefix pf, Trans t) throws IOException
    {
        TransUtil.moveWithRollback_(_f, ((LinkedPrefix)pf)._f, t);
    }

    @Override
    public void prepare_(Token tk) throws IOException
    {
    }

    @Override
    public void delete_() throws IOException
    {
        _f.delete();
    }

    @Override
    public void truncate_(long length) throws IOException
    {
        try (FileOutputStream s = new FileOutputStream(_f.getImplementation())) {
            s.getChannel().truncate(length);
        }
    }

    @Override
    public String toString()
    {
        return _f.toString();
    }
}
