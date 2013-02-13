/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.migration;

import com.aerofs.lib.Util;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SOID;
import com.aerofs.base.id.UniqueID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class EmigrantUtil
{
    private static final int SID_STRING_LEN = SID.ZERO.toStringFormal().length();
    private static final int EMIGRANT_NAME_LEN = SID_STRING_LEN * 2 + 1;

    /**
     * @param sidEmigrateTarget the SID of the store to which the object has been
     * emigrated to. non-null if the deletion is caused by emigration
     *
     * @return the new name of the object to be deleted. the name encodes the
     * emigration information used by detectAndPerformEmmigration_() if
     * sidEmigrateTarget is not null
     *
     */
    public static String getDeletedObjectName_(SOID soid, @Nonnull SID sidEmigrateTarget)
    {
        String name = soid.oid().toStringFormal() + "." + sidEmigrateTarget.toStringFormal();
        assert isEmigrantName(name);
        return name;
    }

    public static boolean isEmigrantName(String name)
    {
        return name.length() == EMIGRANT_NAME_LEN;
    }

    /**
     * @return null if the name doesn't indicates an emigrated object
     */
    public static @Nullable SID getEmigrantTargetSID(String name)
    {
        if (!isEmigrantName(name)) return null;

        try {
            // FIXME: we should not use a string store id
            return new SID(new UniqueID(name, EMIGRANT_NAME_LEN - SID_STRING_LEN,
                    EMIGRANT_NAME_LEN));
        } catch (ExFormatError e) {
            Util.l(EmigrantUtil.class).debug("name format error. ignored for emigration: " + name);
            return null;
        }
    }
}
