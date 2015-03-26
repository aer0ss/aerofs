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

import static com.google.common.base.Preconditions.checkState;


class BlockFile implements IPhysicalFile
{
    private static final Logger l = Loggers.getLogger(BlockFile.class);

    private final BlockStorage _s;
    final SOKID _sokid;
    final Path _path;
    private FileInfo _info;

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

    private FileInfo info_() throws SQLException
    {
        return _info != null ? _info : _s.getFileInfoNullable_(_sokid);
    }

    @Override
    public void prepareForAccessWithoutCoreLock_() throws SQLException
    {
        _info = info_();
    }

    @Override
    public long lengthOrZeroIfNotFile()
    {
        try {
            FileInfo info = info_();
            return info != null ? info._length : 0;
        } catch (SQLException e) {
            l.warn("Failed to determine length of {}", _sokid, e);
            return 0;
        }
    }

    @Override
    public long lastModified() throws IOException
    {
        try {
            FileInfo info = info_();
            if (info == null) throw new ExFileNotFound(_path);
            return info._mtime;
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
    public void onUnexpectedModification_(long expectedMtime) throws IOException
    {
        l.error("corrupted block file {} {}", this, expectedMtime);
        checkState(false, "corrupted block file %s %s", this, expectedMtime);
    }

    @Override
    public void onContentHashMismatch_() throws IOException
    {
        l.error("corrupted block file {}", this);
        checkState(false, "corrupted block file %s", this);
    }

    @Override
    public boolean exists_()
    {
        try {
            return FileInfo.exists(info_());
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
    public InputStream newInputStream() throws IOException
    {
        try {
            FileInfo info = info_();
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
