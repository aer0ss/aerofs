/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.proto.Sp.PBFullName;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.gson.annotations.SerializedName;

import javax.annotation.Nonnull;

/**
 * This class represents a user's full name
 */
public class FullName
{
    @SerializedName("first_name") final public String _first;
    @SerializedName("last_name") final public String _last;
    // mark as transient to avoid GSON serialization
    private transient PBFullName _pb;

    public static FullName fromExternal(@Nonnull String first, @Nonnull String last)
            throws ExBadArgs
    {
        String firstName = first.trim();
        String lastName = last.trim();
        if (firstName.isEmpty() || lastName.isEmpty()) {
            throw new ExBadArgs("First and last names must not be empty");
        }
        return new FullName(firstName, lastName);
    }

    public FullName(@Nonnull String first, @Nonnull String last)
    {
        _first = first;
        _last = last;
    }

    public PBFullName toPB()
    {
        if (_pb == null) {
            _pb = PBFullName.newBuilder()
                    .setFirstName(_first)
                    .setLastName(_last)
                    .build();
        }

        return _pb;
    }

    public FullName fromPB(PBFullName pb)
    {
        FullName fn = new FullName(pb.getFirstName(), pb.getLastName());
        fn._pb = pb;
        return fn;
    }

    /**
     * @return a string with first and last name combined
     */
    public String getString()
    {
        // call trim() in case the first or last name is empty.
        String ret = (_first + " " + _last).trim();
        return ret.isEmpty() ? "Unknown User" : ret;
    }

    /**
     * @return true if one of the first or last name is null.
     */
    public boolean isFirstOrLastNameEmpty()
    {
        return _first.isEmpty() || _last.isEmpty();
    }

    @Override
    public boolean equals(Object o)
    {
        return o == this || (o != null && o instanceof FullName
                                     && _first.equals(((FullName)o)._first)
                                     && _last.equals(((FullName)o)._last));
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(_first, _last);
    }
}
