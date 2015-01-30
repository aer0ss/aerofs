/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.TransUtil;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentBlockHash;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class BlockPrefix implements IPhysicalPrefix
{
    private final BlockStorage _s;
    final SOKID _sokid;
    final InjectableFile _f;
    ContentBlockHash _hash;

    BlockPrefix(BlockStorage s, SOKID sokid, InjectableFile f)
    {
        _s = s;
        _sokid = sokid;
        _f = f;
    }

    @Override
    public long getLength_()
    {
        return _f.getLengthOrZeroIfNotFile();
    }

    @Override
    public InputStream newInputStream_() throws IOException
    {
        return new FileInputStream(_f.getImplementation());
    }

    @Override
    public OutputStream newOutputStream_(boolean append) throws IOException
    {
        return new FileOutputStream(_f.getImplementation(), append);
    }

    @Override
    public void moveTo_(IPhysicalPrefix pf, Trans t) throws IOException
    {
        BlockPrefix to = (BlockPrefix)pf;
        // TODO: do we need the rollback to preserve the destination prefix?
        TransUtil.moveWithRollback_(_f, to._f, t);
    }

    @Override
    public void prepare_(Token tk) throws IOException
    {
        if (_hash == null) _hash = _s.prepare_(this, tk);
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
}
