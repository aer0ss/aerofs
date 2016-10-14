/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.restless.util;

import com.sun.jersey.core.header.MatchingEntityTag;
import com.sun.jersey.core.header.reader.HttpHeaderReader;

import javax.annotation.Nullable;
import javax.ws.rs.core.EntityTag;
import java.text.ParseException;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represent a (possibly empty) set of entity tags provided in an If-Match or If-None-Match header
 */
public class EntityTagSet
{
    private final @Nullable Set<? extends EntityTag> _set;

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
