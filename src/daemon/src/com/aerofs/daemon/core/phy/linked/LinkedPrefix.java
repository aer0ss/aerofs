package com.aerofs.daemon.core.phy.linked;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.injectable.InjectableFile;

public class LinkedPrefix implements IPhysicalPrefix
{
    final InjectableFile _f;

    public LinkedPrefix(InjectableFile.Factory factFile, SOCKID k, String pathAuxRoot)
    {
        _f = factFile.create(Util.join(pathAuxRoot, LibParam.AuxFolder.PREFIX._name,
                LinkedStorage.makeAuxFileName(k.sokid())));
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
        LinkedStorage.moveWithRollback_(_f, ((LinkedPrefix) pf)._f, t);
    }

    @Override
    public ContentHash prepare_(Token tk) throws IOException
    {
        return null;
    }

    @Override
    public String toString()
    {
        return _f.toString();
    }
}
