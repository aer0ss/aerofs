package com.aerofs.daemon.core.migration;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExFormatError;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.UniqueID;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.apache.log4j.Logger;
import javax.annotation.Nullable;

/**
 * Unlike the ImmigrantDetector class, this class doesn't perform actual cross-store
 * movement. Instead, it detects object emigration initiated by other peers,
 * and trigers the immigration process which does the actual movement. The
 * detection is done by examine the name of an deleted object. The trigerring
 * is done indireclty by downloading the target object.
 */
public class EmigrantCreator
{
    static final Logger l = Util.l(EmigrantCreator.class);

    private final IStores _ss;
    private final IMapSID2SIndex _sid2sidx;
    private final IMapSIndex2SID _sidx2sid;

    private static final int SID_STRING_LEN = SID.ZERO.toStringFormal().length();
    private static final int EMIGRANT_NAME_LEN = SID_STRING_LEN * 2 + 1;

    @Inject
    public EmigrantCreator(IStores ss, IMapSID2SIndex sid2sidx, IMapSIndex2SID sidx2sid)
    {
        _ss = ss;
        _sid2sidx = sid2sidx;
        _sidx2sid = sidx2sid;
    }

    /**
     * @param sidEmigrateTarget the SID of the store to which the object has been
     * emigrated to. non-null if the deletion is caused by emigration
     *
     * @return the new name of the object to be deleted. the name encodes the
     * emigration information used by detectAndPerformEmmigration_() if
     * sidEmigrateTarget is not null
     *
     */
    public static String getDeletedObjectName_(SOID soid,
        @Nullable SID sidEmigrateTarget)
    {
        String name = soid.oid().toStringFormal();
        if (sidEmigrateTarget != null) {
            name += "." + sidEmigrateTarget.toStringFormal();
            assert isEmigrantName(name);
        }
        return name;
    }

    static boolean isEmigrantName(String name)
    {
        return name.length() == EMIGRANT_NAME_LEN;
    }

    /**
     * @return null if the name doesn't indicates an emigrated object
     */
    static SID getEmigrantTargetSID(String name)
    {
        if (!isEmigrantName(name)) return null;

        try {
            // FIXME: we should not use a string store id
            return new SID(new UniqueID(name, EMIGRANT_NAME_LEN - SID_STRING_LEN,
                    EMIGRANT_NAME_LEN));
        } catch (ExFormatError e) {
            l.info("name format error. ignored for emigration: " + name);
            return null;
        }
    }

    /**
     * @param oidParent the parent OID of the object
     * @param name the name of the object
     * @return a list of SIDs ready to be filled into the
     * PBMeta.emigrant_target_ancestor_sid field for the given object. an empty
     * list for non-emigrant objects or if the target store doesn't exist locally
     */
    public List<SID> getEmigrantTargetAncestorSIDsForMeta_(OID oidParent, String name)
            throws SQLException
    {
        if (!oidParent.equals(OID.TRASH)) return Collections.emptyList();
        SID sid = getEmigrantTargetSID(name);
        if (sid == null) return Collections.emptyList();
        SIndex sidx = _sid2sidx.getNullable_(sid);
        if (sidx == null) return Collections.emptyList();

        SIndex sidxRoot = _ss.getRoot_();

        // get the parent store for non-root stores
        if (!sidx.equals(sidxRoot)) sidx = _ss.getParent_(sidx);

        ArrayList<SID> ret = Lists.newArrayListWithCapacity(2);
        while (true) {
            ret.add(_sidx2sid.get_(sidx));
            if (sidx.equals(sidxRoot)) break;
            sidx = _ss.getParent_(sidx);
        }

        return ret;
    }
}
