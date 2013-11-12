package com.aerofs.daemon.rest.util;

import com.aerofs.base.BaseUtil;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;
import com.sun.jersey.core.header.MatchingEntityTag;
import com.sun.jersey.core.header.reader.HttpHeaderReader;

import javax.annotation.Nullable;
import javax.ws.rs.core.EntityTag;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Set;

/**
 * Etag-related helpers
 */
public class EntityTagUtil
{
    private final NativeVersionControl _nvc;

    @Inject
    public EntityTagUtil(NativeVersionControl nvc)
    {
        _nvc = nvc;
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

    public static @Nullable Set<MatchingEntityTag> parseSet(String str)
    {
        if (str == null || str.isEmpty()) return null;
        try {
            return HttpHeaderReader.readMatchingEntityTag(str);
        } catch (ParseException e) {
            return null;
        }
    }

    public static boolean match(Set<? extends EntityTag> matching, EntityTag etag)
    {
        return matching == MatchingEntityTag.ANY_MATCH || matching.contains(etag);
    }

    /**
     * @return HTTP Entity tag for a given SOID
     *
     * We use version hashes as entity tags for simplicity
     */
    public EntityTag etagForFile(SOID soid) throws SQLException
    {
        return new EntityTag(BaseUtil.hexEncode(_nvc.getVersionHash_(soid)));
    }
}
