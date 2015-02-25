/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.daemon.core.phy.DigestSerializer;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.PrefixOutputStream;
import com.aerofs.daemon.core.phy.TransUtil;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentBlockHash;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;

import static com.aerofs.daemon.core.phy.PrefixOutputStream.hashFile;
import static com.aerofs.daemon.core.phy.PrefixOutputStream.partialDigest;

/**
 * Prefixes storage scheme:
 *
 * auxroot/
 *      p/
 *          <sidx>/
 *              <oid>/
 *                  <kidx>[-<scope>]            prefix
 *                  <kidx>[-<scope>].hash       incremental hash state
 *
 * A prefix is considered invalid and discarded if the size of the prefix and the
 * incremental hash do not match.
 *
 * TODO: incremental chunking
 *
 * regular scheme for tail chunk
 * extra state file for whole-file hash
 * complete chunk:
 *      - append chunk hash to ContentBlockHash (stored in another file? DB?)
 *      - renamed to content hash (dedup)
 * speculative chunk upload in background?
 */
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
        return _f.lengthOrZeroIfNotFile();
    }

    @Override
    public PrefixOutputStream newOutputStream_(boolean append) throws IOException
    {
        final MessageDigest md = partialDigest(_f, append);
        _f.getParentFile().ensureDirExists();
        return new PrefixOutputStream(new FileOutputStream(_f.getImplementation(), append) {
            @Override
            public void close() throws IOException
            {
                // persist hash state
                try (OutputStream out = hashFile(_f).newOutputStream()) {
                    if (_f.length() > 0) out.write(DigestSerializer.serialize(md));
                } finally {
                    super.close();
                }
            }
        }, md);
    }

    @Override
    public void moveTo_(IPhysicalPrefix pf, Trans t) throws IOException
    {
        BlockPrefix to = (BlockPrefix)pf;
        to._f.getParentFile().ensureDirExists();
        TransUtil.moveWithRollback_(_f, to._f, t);
        TransUtil.moveWithRollback_(hashFile(_f), hashFile(to._f), t);
    }

    @Override
    public void prepare_(Token tk) throws IOException
    {
        if (_hash == null) _hash = _s.prepare_(this, tk);
    }

    @Override
    public void delete_() throws IOException
    {
        _f.deleteOrThrowIfExist();
        cleanup_();
    }

    void cleanup_()
    {
        hashFile(_f).deleteIgnoreError();

        InjectableFile p = _f.getParentFile();
        String[] children = _f.list();
        if (children == null || children.length == 0) p.deleteIgnoreError();
    }
}
