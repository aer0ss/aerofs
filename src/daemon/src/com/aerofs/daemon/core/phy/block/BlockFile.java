/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.block.BlockStorageDatabase.FileInfo;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.ex.ExFileNotFound;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Date;


class BlockFile implements IPhysicalFile
{
    private static final Logger l = Loggers.getLogger(BlockFile.class);

    private final BlockStorage _s;
    final SOKID _sokid;
    final Path _path;

    BlockFile(BlockStorage s, SOKID sokid, Path path)
    {
        _s = s;
        _sokid = sokid;
        _path = path;
    }

    @Override
    public String toString()
    {
        return "BlockFile(" + _sokid + "," + _path + ")";
    }

    @Override
    public long getLength_()
    {
        try {
            return _s.getFileInfo_(_sokid)._length;
        } catch (SQLException e) {
            l.warn("Failed to determine length of {}", _sokid, e);
            return 0;
        }
    }

    @Override
    public long getLastModificationOrCurrentTime_() throws IOException
    {
        try {
            return _s.getFileInfo_(_sokid)._mtime;
        } catch (SQLException e) {
            l.warn("Failed to determine mtime", e);
            return new Date().getTime();
        }
    }

    @Override
    public boolean wasModifiedSince(long mtime, long len) throws IOException
    {
        return false;
    }

    @Override
    public boolean exists_()
    {
        try {
            return _s.getFileInfo_(_sokid).exists();
        } catch (SQLException e) {
            l.warn("Failed to determine existence of {}", _sokid, e);
            return false;
        }
    }

    @Override
    public String getAbsPath_()
    {
        return null;
    }

    @Override
    public InputStream newInputStream_() throws IOException
    {
        try {
            FileInfo info = _s.getFileInfoNullable_(_sokid);
            if (!FileInfo.exists(info)) throw new ExFileNotFound(_path);
            return _s.readChunks(info._chunks);
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void create_(PhysicalOp op, Trans t) throws IOException, SQLException
    {
        l.debug("{}.create_({})", this, op);
        if (op == PhysicalOp.APPLY) _s.create_(this, t);
    }

    @Override
    public void delete_(PhysicalOp op, Trans t) throws IOException, SQLException
    {
        l.debug("{}.delete_({})", this, op);
        if (op == PhysicalOp.APPLY) _s.delete_(this, t);
    }

    @Override
    public void move_(ResolvedPath to, KIndex kidx, PhysicalOp op, Trans t)
            throws IOException, SQLException
    {
        l.debug("{}.move_({}, {}, {})", this, to, kidx, op);
        if (op == PhysicalOp.APPLY) _s.move_(this, to, kidx, t);
    }

    @Override
    public void updateSOID_(SOID soid, Trans t) throws IOException, SQLException
    {
        l.debug("{}.alias_({})", soid);
        _s.updateSOID_(_sokid, soid, t);
    }
}
