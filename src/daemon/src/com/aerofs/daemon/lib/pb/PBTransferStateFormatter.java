/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.lib.pb;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.ITransferStateListener.Key;
import com.aerofs.daemon.core.net.ITransferStateListener.Value;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.tcpmt.TCP;
import com.aerofs.daemon.transport.xmpp.Jingle;
import com.aerofs.daemon.transport.xmpp.Zephyr;
import com.aerofs.lib.FullName;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.proto.RitualNotifications.PBSOCID;
import com.aerofs.proto.RitualNotifications.PBTransferEvent;
import com.aerofs.proto.RitualNotifications.PBTransportMethod;
import com.google.common.base.Objects;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class PBTransferStateFormatter
{
    private Logger l = Loggers.getLogger(PBTransferStateFormatter.class);

    private final DirectoryService _ds;
    private final UserAndDeviceNames _nr;

    @Inject
    public PBTransferStateFormatter(DirectoryService ds, UserAndDeviceNames nr)
    {
        _ds = ds;
        _nr = nr;
    }

    public PBNotification createClearTransfers()
    {
        return PBNotification.newBuilder()
                .setType(Type.CLEAR_TRANSFERS)
                .build();
    }

    public PBNotification formatDownloadState(Key key, Value value)
    {
        return formatTransferState(key, value, false);
    }

    public PBNotification formatUploadState(Key key, Value value)
    {
        return formatTransferState(key, value, true);
    }

    private PBNotification formatTransferState(Key key, Value value, boolean upload)
    {
        SOCID socid = key._socid;
        PBSOCID pbsocid = PBSOCID.newBuilder()
                .setSidx(socid.sidx().getInt())
                .setOid(socid.oid().toPB())
                .setCid(socid.cid().getInt())
                .build();

        DID did = key._ep.did();

        PBTransferEvent.Builder bd = PBTransferEvent.newBuilder()
                .setUpload(upload)
                .setSocid(pbsocid)
                .setDeviceId(did.toPB())
                .setDisplayName(formatDisplayName_(did))
                .setDone(value._done)
                .setTotal(value._total)
                .setFailed(value._failed)
                .setTransport(formatTransportMethod(key._ep.tp()));

        Path path;
        try {
            path = _ds.resolveNullable_(socid.soid());
        } catch (SQLException e) {
            l.warn(Util.e(e));
            path = null;
        }

        if (path != null) bd.setPath(path.toPB());

        return PBNotification.newBuilder()
                .setType(Type.TRANSFER)
                .setTransfer(bd)
                .build();
    }

    private PBTransportMethod formatTransportMethod(ITransport transport)
    {
        if (transport instanceof TCP) {
            return PBTransportMethod.TCP;
        } else if (transport instanceof Jingle) {
            return PBTransportMethod.JINGLE;
        } else if (transport instanceof Zephyr) {
            return PBTransportMethod.ZEPHYR;
        } else if (transport == null) {
            return PBTransportMethod.NOT_AVAILABLE;
        } else {
            l.warn("Unable to format transport method");
            return PBTransportMethod.UNKNOWN;
        }
    }

    /**
     * Resolve the DID into either username or device name depending on if the owner
     *   is the local user. It will make SP calls to update local database if necessary,
     *   and it will return the proper unknown label if it's unable to resolve the DID.
     *
     * @param did
     * @return the username of the owner of the device if it's not the local user
     *   or the device name of the device if it is the local user
     *   or the proper error label if we are unable to resolve the DID.
     */
    private @Nonnull String formatDisplayName_(DID did)
    {
        try {
            return formatDisplayNameImpl_(did);
        } catch (Exception ex) {
            l.warn("Failed to lookup display name for {}", did, ex);
            return S.LBL_UNKNOWN_USER;
        }
    }

    private @Nonnull String formatDisplayNameImpl_(DID did)
            throws Exception
    {
        UserID owner = _nr.getDeviceOwnerNullable_(did);

        if (owner == null) return S.LBL_UNKNOWN_USER;
        else if (_nr.isLocalUser(owner)) {
            String devicename = _nr.getDeviceNameNullable_(did);

            if (devicename == null) {
                List<DID> unresolved = Collections.singletonList(did);
                if (_nr.updateLocalDeviceInfo_(unresolved)) {
                    devicename = _nr.getDeviceNameNullable_(did);
                }
            }

            return "My " + Objects.firstNonNull(devicename, S.LBL_UNKNOWN_DEVICE);
        } else {
            FullName username = _nr.getUserNameNullable_(owner);

            if (username == null) {
                List<DID> unresolved = Collections.singletonList(did);
                if (_nr.updateLocalDeviceInfo_(unresolved)) {
                    username = _nr.getUserNameNullable_(owner);
                }
            }

            return username == null ? S.LBL_UNKNOWN_USER : username.toString();
        }
    }
}
