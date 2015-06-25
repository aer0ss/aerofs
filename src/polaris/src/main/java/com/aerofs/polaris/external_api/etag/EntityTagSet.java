package com.aerofs.polaris.external_api.etag;

import org.glassfish.jersey.message.internal.HttpHeaderReader;
import org.glassfish.jersey.message.internal.MatchingEntityTag;

import javax.annotation.Nullable;
import javax.ws.rs.core.EntityTag;
import java.text.ParseException;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/* N.B. This class is duplicate of com.aerofs.restless.util.EntityTagSet. It exists because
 * polaris depends on jersey 2.x via Baseline and restless uses jersey 1.x.
 */
public class EntityTagSet
{
    private final @Nullable
    Set<? extends EntityTag> _set;

    /**
     * ctor from string, for easy use in Jersey resource methods
     */
    public EntityTagSet(String s)
    {
        _set = parseSet(s);
    }

    public boolean isValid()
    {
        return _set != null;
    }

    public boolean matches(EntityTag etag)
    {
        return _set == MatchingEntityTag.ANY_MATCH || checkNotNull(_set).contains(etag);
    }

    private static @Nullable Set<MatchingEntityTag> parseSet(String str)
    {
        if (str == null || str.isEmpty()) return null;
        try {
            return HttpHeaderReader.readMatchingEntityTag(str);
        } catch (ParseException e) {
            return null;
        }
    }
}
