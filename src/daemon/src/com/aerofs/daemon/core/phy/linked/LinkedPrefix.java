package com.aerofs.daemon.core.phy.linked;

import java.io.IOException;
import java.security.MessageDigest;

import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.PrefixOutputStream;
import com.aerofs.daemon.core.phy.TransUtil;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;

import static com.aerofs.daemon.core.phy.PrefixOutputStream.*;

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
 */
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
        return _f.lengthOrZeroIfNotFile();
    }

    @Override
    public PrefixOutputStream newOutputStream_(boolean append) throws IOException
    {
        final MessageDigest md = partialDigest(_f, append);
        _f.getParentFile().ensureDirExists();
        return new PrefixOutputStream(_f.newOutputStream(append), md) {
            @Override
            public void close() throws IOException
            {
                try {
                    super.close();
                } finally {
                    if (_f.lengthOrZeroIfNotFile() > 0) {
                        LinkedStorage.l.debug("persist partial hash {}", _f);
                        persistDigest(md, hashFile(_f));
                    }
                }
            }
        };
    }

    @Override
    public void moveTo_(IPhysicalPrefix pf, Trans t) throws IOException
    {
        InjectableFile f = ((LinkedPrefix) pf)._f;
        f.getParentFile().ensureDirExists();
        f.deleteOrThrowIfExist();
        hashFile(f).deleteOrThrowIfExist();

        TransUtil.moveWithRollback_(_f, f, t);
        TransUtil.moveWithRollback_(hashFile(_f), hashFile(f), t);
    }

    @Override
    public void delete_() throws IOException
    {
        _f.deleteOrThrowIfExist();
        cleanup_();
    }

    void cleanup_()
    {
        InjectableFile hf = hashFile(_f);
        if (hf.exists()) {
            LinkedStorage.l.debug("discard partial hash {}", _f);
            hf.deleteIgnoreError();
        }

        InjectableFile p = _f.getParentFile();
        String[] children = _f.list();
        if (children == null || children.length == 0) p.deleteIgnoreError();
    }

    @Override
    public String toString()
    {
        return _f.toString();
    }
}
