/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.C.AuxFolder;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsAuxRoot;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;

/**
 * IPhysicalStorage interface to a block-based storage backend
 *
 * The logic is split between a backend-agnostic database, which keeps track of block usage and a
 * backend which handles the actual storage, either locally or remotely.
 */
class BlockStorage implements IPhysicalStorage
{
    private static final Logger l = Util.l(BlockStorage.class);

    private final TransManager _tm;
    private final InjectableFile.Factory _fileFactory;
    private final CfgAbsAuxRoot _absAuxRoot;

    private InjectableFile _prefixDir;

    private final IBlockStorageBackend _bsb;
    private final BlockStorageDatabase _bsdb;

    private final BlockRevProvider _revProvider = new BlockRevProvider();

    public class FileAlreadyExistsException extends IOException
    {
        private static final long serialVersionUID = 0L;
        public FileAlreadyExistsException(String s) { super(s); }
    }

    @Inject
    public BlockStorage(CfgAbsAuxRoot absAuxRoot, TransManager tm,
            InjectableFile.Factory fileFactory, IBlockStorageBackend bsb, BlockStorageDatabase bsdb)
    {
        _tm = tm;
        _fileFactory = fileFactory;
        _absAuxRoot = absAuxRoot;
        _bsb = bsb;
        _bsdb = bsdb;

    }

    @Override
    public void init_() throws IOException
    {
        _prefixDir = _fileFactory.create(_absAuxRoot.get(), AuxFolder.PREFIX._name);
        if (!_prefixDir.isDirectory()) _prefixDir.mkdirs();

        _bsb.init_();
        try {
            _bsdb.init_();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public IPhysicalFile newFile_(SOKID sokid, Path path)
    {
        return new BlockFile(this, sokid, path);
    }

    @Override
    public IPhysicalFolder newFolder_(SOID soid, Path path)
    {
        return new BlockFolder(this, soid, path);
    }

    @Override
    public IPhysicalPrefix newPrefix_(SOCKID k)
    {
        // TODO: take component into account if we ever use components other than META/CONTENT
        return new BlockPrefix(this, k, _fileFactory.create(_prefixDir, makeFileName(k.sokid())));
    }

    /**
     * Update database after complete prefix download
     *
     * By the time this method is called:
     *      1. the prefix file should be fully downloaded
     *      2. the contents of the prefix file should have been chunked and stored by the backend
     */
    @Override
    public long apply_(IPhysicalPrefix prefix, IPhysicalFile file, boolean wasPresent, long mtime,
            Trans t) throws IOException, SQLException
    {
        assert false : "Not implemented";
        return mtime;
    }

    @Override
    public IPhysicalRevProvider getRevProvider()
    {
        return _revProvider;
    }

    @Override
    public void createStore_(SIndex sidx, Path path, Trans t) throws IOException, SQLException
    {
        // TODO: fw to backend?
    }

    @Override
    public void deleteStore_(SIndex sidx, Path path, PhysicalOp op, Trans t)
            throws IOException, SQLException
    {
        // TODO: fw to backend?
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private String makeFileName(SOKID sokid)
    {
        return sokid.sidx().toString() + '-' +
                sokid.oid().toStringFormal() + '-' +
                sokid.kidx();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * "Prepare" a fully downloaded prefix before moving it into persistent storage
     *
     * We use this method to perform the actual movement as the backend may use remote storage.
     */
    ContentHash prepare_(BlockPrefix prefix, Token tk) throws IOException
    {
        assert false : "Not implemented";
        return null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    void create_(BlockFile file, Trans t) throws IOException, SQLException
    {
        assert false : "Not implemented";
    }

    void delete_(BlockFile file, Trans t) throws IOException, SQLException
    {
        assert false : "Not implemented";
    }

    void move_(BlockFile from, BlockFile to, Trans t) throws IOException, SQLException
    {
        assert false : "Not implemented";
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    class BlockRevProvider implements IPhysicalRevProvider
    {
        @Override
        public Collection<Child> listRevChildren_(Path path) throws Exception
        {
            assert false : "Not implemented";
            return null;
        }

        @Override
        public Collection<Revision> listRevHistory_(Path path) throws Exception
        {
            assert false : "Not implemented";
            return null;
        }

        @Override
        public RevInputStream getRevInputStream_(Path path, byte[] index) throws Exception
        {
            assert false : "Not implemented";
            return null;
        }
    }
}
