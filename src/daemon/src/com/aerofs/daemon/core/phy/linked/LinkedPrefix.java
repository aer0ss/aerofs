package com.aerofs.daemon.core.phy.linked;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;

import com.aerofs.daemon.core.phy.DigestSerializer;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.PrefixOutputStream;
import com.aerofs.daemon.core.phy.TransUtil;
import com.aerofs.daemon.core.tc.Token;
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
        return new PrefixOutputStream(new FileOutputStream(_f.getImplementation(), append) {
            @Override
            public void close() throws IOException
            {
                // persist hash state
                try (OutputStream out = hashFile(_f).newOutputStream()) {
                    if (_f.lengthOrZeroIfNotFile() > 0) {
                        out.write(DigestSerializer.serialize(md));
                    }
                } finally {
                    super.close();
                }
            }
        }, md);
    }

    @Override
    public void moveTo_(IPhysicalPrefix pf, Trans t) throws IOException
    {
        ((LinkedPrefix)pf)._f.getParentFile().ensureDirExists();
        TransUtil.moveWithRollback_(_f, ((LinkedPrefix) pf)._f, t);
        TransUtil.moveWithRollback_(hashFile(_f), hashFile(((LinkedPrefix)pf)._f), t);
    }

    @Override
    public void prepare_(Token tk) throws IOException
    {
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

    @Override
    public String toString()
    {
        return _f.toString();
    }
}
