package com.aerofs.daemon.rest.util;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.rest.handler.RestContentHelper;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import javax.ws.rs.core.EntityTag;
import java.security.MessageDigest;
import java.sql.SQLException;

/**
 * Etag-related helpers
 */
public class EntityTagUtil extends ContentEntityTagUtil
{
    private final DirectoryService _ds;

    @Inject
    public EntityTagUtil(DirectoryService ds, RestContentHelper helper)
    {
        super(helper);
        _ds = ds;
    }

    /**
     * @return HTTP Entity tag for a given SOID
     *
     * We use version hashes as entity tags for simplicity
     */
    public EntityTag etagForMeta(SOID soid) throws SQLException
    {
        OA oa = _ds.getOANullable_(soid);
        MessageDigest md = BaseSecUtil.newMessageDigestMD5();
        if (oa != null) {
            md.update(oa.parent().getBytes());
            md.update(BaseUtil.string2utf(oa.name()));
            CA ca = oa.isFile() && !oa.isExpelled() ? oa.caMasterNullable() : null;
            if (ca != null) {
                md.update(BaseUtil.toByteArray(ca.length()));
                md.update(BaseUtil.toByteArray(ca.mtime()));
            }
        }
        return new EntityTag(BaseUtil.hexEncode(md.digest()), true);
    }
}
