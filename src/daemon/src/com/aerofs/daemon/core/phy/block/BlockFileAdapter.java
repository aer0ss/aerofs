/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalObject;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

class BlockFileAdapter implements IPhysicalFile
{
    private final BlockExportedFile exportedFile;
    public final BlockFile blockFile;
    private final FrequentDefectSender _fds;

    public BlockFileAdapter(BlockStorage bs, SOKID sokid, Path path,
            InjectableFile.Factory fileFactory, FrequentDefectSender fds)
    {
        exportedFile = (bs.exportRoot() != null) ?
                new BlockExportedFile(bs, sokid, path, fileFactory) : null ;
        blockFile = new BlockFile(bs, sokid, path);
        _fds = fds;
    }

    @Override
    public long getLength_()
    {
        return blockFile.getLength_();
    }

    @Nullable
    @Override
    public ContentHash getHash_()
    {
        return blockFile.getHash_();
    }

    @Override
    public long getLastModificationOrCurrentTime_()
            throws IOException
    {
        return blockFile.getLastModificationOrCurrentTime_();
    }

    @Override
    public boolean wasModifiedSince(long mtime, long len)
            throws IOException
    {
        return blockFile.wasModifiedSince(mtime, len);
    }

    @Override
    public String getAbsPath_()
    {
        return blockFile.getAbsPath_();
    }

    public String exportedAbsPath()
    {
        return exportedFile.exportedAbsPath();
    }

    public void onCommit(BlockPrefix from, InjectableFile to)
    {
        if (exportedFile != null) {
            try {
                movePrefixToExport(from, to);
            } catch (IOException e) {
                // Log failures to SV, but don't make anything else fail -  BlockStorage's
                // canonical representation is fine, and the exported data will become
                // correct on the next write received from the network.
                _fds.logSendAsync("autoexport failed: " + from + " " + to, e);
            }
        } else {
            from._f.deleteIgnoreError();
        }
    }

    private void movePrefixToExport(BlockPrefix from, InjectableFile target)
            throws IOException
    {
        InjectableFile targetParent = target.getParentFile();
        // Ensure exportedAbsPath's parent exists before moving the prefix file
        // to the exported folder.  Ensure the target file is not actually present,
        // since on Windows renameTo fails if the target already exists.
        targetParent.ensureDirExists();
        target.deleteIgnoreError();
        from._f.moveInSameFileSystem(target);
        // Set exported file to read-only as a hint/reminder to the user that they
        // shouldn't be modifying the exported file and expecting it to sync.
        target.getImplementation().setReadOnly();
    }

    @Override
    public InputStream newInputStream_()
            throws IOException
    {
        return blockFile.newInputStream_();
    }

    @Override
    public void create_(PhysicalOp op, Trans t)
            throws IOException, SQLException
    {
        blockFile.create_(op, t);
    }

    @Override
    public void delete_(PhysicalOp op, Trans t)
            throws IOException, SQLException
    {
        if (op == PhysicalOp.APPLY) {
            blockFile.delete_(op, t);
            if (exportedFile != null) {
                exportedFile.delete_(t);
            }
        }
    }

    @Override
    public void move_(IPhysicalObject to, PhysicalOp op, Trans t)
            throws IOException, SQLException
    {
        if (op == PhysicalOp.APPLY) {
            BlockFileAdapter target = (BlockFileAdapter)to;
            blockFile.move_(target.blockFile, op, t);
            if (exportedFile != null) {
                exportedFile.move_(target.exportedFile, t);
            }
        }
    }
}
