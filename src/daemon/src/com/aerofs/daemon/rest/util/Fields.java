/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.rest.util;

import com.aerofs.base.ParamFactory;
import com.google.common.collect.ImmutableSet;

public class Fields
{
    private final ImmutableSet<String> fields;

    private Fields(ImmutableSet<String> fields)
    {
        this.fields = fields;
    }

    @ParamFactory
    public static Fields create(String s)
    {
        return new Fields(ImmutableSet.copyOf(s.split(",")));
    }

    public boolean isRequested(String field)
    {
        return fields.contains(field);
    }
}
