/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.local;

import com.aerofs.daemon.core.phy.block.IBlockStorageBackend;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.common.io.ByteStreams;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static com.aerofs.daemon.core.phy.block.BlockUtil.isOneBlock;

/**
 * Blocks are stored in a prefix-tree on the file system:
 *      the first two hex digits of the key determine the top-level folder
 *      the next two hex digits determine the next folder and so on
 *      the last digits determine the file name at the deepest folder level
 *
 * The depth of the tree need not be fixed or uniform but for simplicity the current implementation
 * uses a fixed uniform depth of 2
 */
public class LocalBackend implements IBlockStorageBackend
{
    private final static Logger l = Util.l(LocalBackend.class);

    private final InjectableFile.Factory _fileFactory;
    private final InjectableFile _rootDir;

    @Inject
    public LocalBackend(CfgAbsRootAnchor absRootAnchor, InjectableFile.Factory fileFactory)
    {
        _fileFactory = fileFactory;
        _rootDir = _fileFactory.create(absRootAnchor.get());
    }

    @Override
    public void init_() throws IOException
    {
        if (!_rootDir.isDirectory()) _rootDir.mkdirs();
    }

    @Override
    public InputStream getBlock(ContentHash key) throws IOException
    {
        InjectableFile block = getBlockFile(key);
        if (!block.exists()) throw new FileNotFoundException();
        return block.newInputStream();
    }

    @Override
    public EncoderWrapping wrapForEncoding(OutputStream out) throws IOException
    {
        return new EncoderWrapping(out, null);
    }

    @Override
    public void putBlock(ContentHash key, InputStream input, long decodedLength, Object encoderData)
            throws IOException
    {
        InjectableFile block = getBlockFile(key);
        if (!block.exists()) {
            if (!block.getParentFile().exists()) block.getParentFile().mkdirs();
            block.createNewFile();
        }
        ByteStreams.copy(input, block.newOutputStream());
    }

    // 2 hex digits per level -> 256-ary prefix tree
    private static final int HEX_DIGIS_PER_LEVEL = 2;

    private InjectableFile getBlockFile(ContentHash key)
    {
        assert isOneBlock(key);
        String k = key.toHex();
        return _fileFactory.create(
                _fileFactory.create(
                        _fileFactory.create(_rootDir, k.substring(0, HEX_DIGIS_PER_LEVEL)),
                        k.substring(HEX_DIGIS_PER_LEVEL, 2*HEX_DIGIS_PER_LEVEL)),
                k.substring(2*HEX_DIGIS_PER_LEVEL));
    }
}
