package com.aerofs.daemon.rest.util;

import javax.annotation.Nullable;
import javax.ws.rs.core.EntityTag;

public class EntityTagParser
{
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

    // No instantiation.
    private EntityTagParser(){}
}
