package com.aerofs.daemon.rest.util;

import com.aerofs.base.BaseUtil;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import javax.ws.rs.core.EntityTag;
import java.security.MessageDigest;
import java.sql.SQLException;

/**
 * Etag-related helpers
 */
public class EntityTagUtil
{
    private final DirectoryService _ds;
    private final NativeVersionControl _nvc;

    @Inject
    public EntityTagUtil(DirectoryService ds, NativeVersionControl nvc)
    {
        _ds = ds;
        _nvc = nvc;
    }

    /**
     * @return HTTP Entity tag for a given SOID
     *
     * We use version hashes as entity tags for simplicity
     */
    public EntityTag etagForMeta(SOID soid) throws SQLException
    {
        return etagForMeta(_ds.getOANullable_(soid));
    }

    public EntityTag etagForMeta(OA oa)
    {
        MessageDigest md = SecUtil.newMessageDigestMD5();
        if (oa != null) {
            md.update(oa.parent().getBytes());
            md.update(BaseUtil.string2utf(oa.name()));
            CA ca = oa.isFile() ? oa.caMasterNullable() : null;
            if (ca != null) {
                md.update(BaseUtil.toByteArray(ca.length()));
                md.update(BaseUtil.toByteArray(ca.mtime()));
            }
        }
        return new EntityTag(BaseUtil.hexEncode(md.digest()), true);
    }

    public EntityTag etagForContent(SOID soid) throws SQLException
    {
        return new EntityTag(BaseUtil.hexEncode(_nvc.getVersionHash_(soid, CID.CONTENT)));
    }

    public static @Nullable EntityTag parse(String str)
    {
        if (str == null || str.isEmpty()) return null;
        try {
            return EntityTag.valueOf(str);
        } catch (IllegalArgumentException e) {
            // fake entity tag that will never match
            // Returning null would cause Range headers to always be honored when accompanied
            // by invalid If-Range which would be unsafe. The "always mismatch" entity ensures
            // that any Range header will be ignored.
            return new EntityTag("!*") {
                @Override public int hashCode() { return super.hashCode(); }
                @Override public boolean equals(Object o) { return false; }
            };
        }
    }
}
