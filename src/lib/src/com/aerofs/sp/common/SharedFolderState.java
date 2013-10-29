/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.common;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.proto.Sp.PBSharedFolderState;

/**
 * See docs/design/sharing_and_migration.txt for information about shared folder states.
 *
 * Note that this enum's structure is very similar to Role, so does its test class.
 */
public enum SharedFolderState
{
    // N.B. the ordinals of these enums are stored in the databases.
    // Be VERY CAREFUL when shifting them!

    JOINED(PBSharedFolderState.JOINED),
    PENDING(PBSharedFolderState.PENDING),
    DELETED(PBSharedFolderState.DELETED);

    private final PBSharedFolderState _pb;

    private SharedFolderState(PBSharedFolderState pb)
    {
        _pb = pb;
    }

    public PBSharedFolderState toPB()
    {
        return _pb;
    }

    public static SharedFolderState fromPB(PBSharedFolderState pb) throws ExBadArgs
    {
        switch (pb) {
        case PENDING: return PENDING;
        case JOINED: return JOINED;
        case DELETED: return DELETED;
        // Since the intput comes from external soruces, we should not throw runtime exceptions
        // which may crash the process.
        default: throw new ExBadArgs("Unknown state: " + pb.toString());
        }
    }

    public static SharedFolderState fromOrdinal(int ordinal)
    {
        assert ordinal >= 0 && ordinal < SharedFolderState.values().length;
        return SharedFolderState.values()[ordinal];
    }
}
