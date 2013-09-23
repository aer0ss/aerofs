/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import com.aerofs.proto.Sp.PBFullName;
import com.google.gson.annotations.SerializedName;

/**
 * This class represents a user's full name
 */
public class FullName
{
    @SerializedName("first_name") final public String _first;
    @SerializedName("last_name") final public String _last;
    // mark as transient to avoid GSON serialization
    private transient PBFullName _pb;

    public FullName(String first, String last)
    {
        assert first != null;
        assert last != null;
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
}
