/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.migration;

import com.aerofs.ids.SID;
import com.aerofs.lib.id.SOID;

import javax.annotation.Nonnull;

import static com.google.common.base.Preconditions.checkArgument;

public class EmigrantUtil
{
    private static final int SID_STRING_LEN = SID.ZERO.toStringFormal().length();
    private static final int EMIGRANT_NAME_LEN = SID_STRING_LEN * 2 + 1;

    /**
     * @param sidEmigrateTarget the SID of the store to which the object has been
     * emigrated to. non-null if the deletion is caused by emigration
     *
     * @return the new name of the object to be deleted. the name encodes the
     * emigration information used by detectAndPerformEmigration_() if
     * sidEmigrateTarget is not null
     *
     */
    public static String getDeletedObjectName_(SOID soid, @Nonnull SID sidEmigrateTarget)
    {
        String name = soid.oid().toStringFormal() + "." + sidEmigrateTarget.toStringFormal();
        checkArgument(isEmigrantName(name));
        return name;
    }

    /**
     * Note: an object is an emigrant iff. 1) isEmigrantName() is true, AND 2) its immediate parent
     * is the trash folder. Since all non-emigrant objects immediately under the trash folder have
     * been renamed to their OIDs, the string length is a reliable indicator of emigrants.
     */
    public static boolean isEmigrantName(String name)
    {
        return name.length() == EMIGRANT_NAME_LEN;
    }
}
