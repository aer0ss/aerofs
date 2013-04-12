/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.net.DID2User;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.IUserAndDeviceNameDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.FullName;
import com.aerofs.lib.S;
import com.aerofs.lib.Throttler;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.proto.Sp.GetDeviceInfoReply;
import com.aerofs.proto.Sp.GetDeviceInfoReply.PBDeviceInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wrapper around DID2User and UserAndDeviceNameDatabase that can make SP calls when the content
 * of the local DB is insufficient.
 */
public class UserAndDeviceNames
{
    private static final Logger l = Loggers.getLogger(UserAndDeviceNames.class);

    public static class UserInfo
    {
        public final UserID _userId;
        public final @Nullable FullName _userName;

        public UserInfo(UserID userId, @Nullable FullName userName)
        {
            assert userId != null;
            this._userId = userId;
            this._userName = userName;
        }

        public String getName()
        {
            return _userName != null ? _userName.toString() : _userId.getString();
        }
    }

    public static class DeviceInfo
    {
        public final UserInfo owner;
        // null if the caller is not the device owner
        public final @Nullable String deviceName;

        public DeviceInfo(UserInfo owner, @Nullable String deviceName)
        {
            assert owner != null;
            this.owner = owner;
            this.deviceName = deviceName;
        }
    }

    private final CfgLocalUser _localUser;
    private final TC _tc;
    private final TransManager _tm;
    private final DID2User _d2u;
    private final IUserAndDeviceNameDatabase _udndb;
    private final SPBlockingClient.Factory _factSP;

    private long _lastSPLoginFailureTime;

    @Inject
    public UserAndDeviceNames(CfgLocalUser localUser, TC tc, TransManager tm, DID2User d2u,
            IUserAndDeviceNameDatabase udndb, SPBlockingClient.Factory factSP)
    {
        _localUser = localUser;
        _tc = tc;
        _tm = tm;
        _d2u = d2u;
        _udndb = udndb;
        _factSP = factSP;
    }

    // TODO (huguesb): refactor HdGetActivities and make this private
    /**
     * Make an SP RPC call to obtain information about a list of DIDs
     * @return false if the call to SP failed
     * @throws ExProtocolError SP results cannot be interpreted unambiguously
     */
    public boolean updateLocalDeviceInfo_(List<DID> dids) throws SQLException, ExProtocolError
    {
        if (_lastSPLoginFailureTime > 0
                && Math.abs(System.currentTimeMillis() - _lastSPLoginFailureTime) < 30 * C.MIN)
            return false;

        GetDeviceInfoReply reply;
        try {
            reply = getDevicesInfoFromSP_(dids);
        } catch (ExBadCredential ex) {
            _lastSPLoginFailureTime = System.currentTimeMillis();
            l.warn("ignored: " + Util.e(ex, IOException.class));
            return false;
        } catch (Exception e) {
            l.warn("ignored: " + Util.e(e, IOException.class));
            return false;
        }

        if (reply.getDeviceInfoCount() != dids.size()) {
            throw new ExProtocolError("server reply count mismatch (" +
                    reply.getDeviceInfoCount() + " != " + dids.size() + ")");
        }

        Trans t = _tm.begin_();
        try {
            for (int i = 0; i < dids.size(); i++) {
                DID did = dids.get(i);
                // guaranteed by the above code
                assert i < reply.getDeviceInfoCount();
                PBDeviceInfo di = reply.getDeviceInfo(i);

                _udndb.setDeviceName_(did, di.hasDeviceName() ? di.getDeviceName() : null, t);
                if (di.hasOwner()) {
                    UserID user = UserID.fromInternal(di.getOwner().getUserEmail());
                    if (_d2u.getFromLocalNullable_(did) == null) _d2u.addToLocal_(did, user, t);
                    FullName fn = new FullName(di.getOwner().getFirstName(),
                            di.getOwner().getLastName());
                    _udndb.setUserName_(user, fn, t);
                }
            }
            t.commit_();
        } finally {
            t.end_();
        }

        return true;
    }

    private GetDeviceInfoReply getDevicesInfoFromSP_(List<DID> dids) throws Exception
    {
        Token tk = _tc.acquire_(Cat.UNLIMITED, "sp-devinfo");
        TCB tcb = null;
        try {
            tcb = tk.pseudoPause_("sp-devinfo");
            SPBlockingClient sp = _factSP.create_(_localUser.get());
            sp.signInRemote();
            List<ByteString> pb = Lists.newArrayListWithExpectedSize(dids.size());
            for (DID did : dids) pb.add(did.toPB());
            return sp.getDeviceInfo(pb);
        } finally {
            if (tcb != null) tcb.pseudoResumed_();
            tk.reclaim_();
        }
    }

    /**
     * Get device info for a collection of DIDs from the local DB
     * @return a DID -> DeviceInfo map
     */
    private void getDeviceInfoMapLocally_(Iterable<DID> dids, Map<DID, DeviceInfo> resolved,
            Set<DID> unresolved) throws Exception
    {
        for (DID did : dids) {
            UserID owner = _d2u.getFromLocalNullable_(did);
            if (owner == null) {
                unresolved.add(did);
                continue;
            }

            FullName name = null;
            String deviceName = null;
            if (owner.equals(_localUser.get())) {
                deviceName = _udndb.getDeviceNameNullable_(did);
                if (deviceName == null) {
                    unresolved.add(did);
                }
            }

            name = _udndb.getUserNameNullable_(owner);
            if (name == null) {
                unresolved.add(did);
            }
            resolved.put(did, new DeviceInfo(new UserInfo(owner, name), deviceName));
        }
    }

    /**
     * Get device info for a collection of DIDs
     * If the local DB is missing information about some devices, a call to SP will be made to
     * retrieve the missing information.
     * @return a DID -> DeviceInfo map
     */
    public Map<DID, DeviceInfo> getDeviceInfoMap_(Iterable<DID> dids) throws Exception
    {
        Map<DID, DeviceInfo> resolved = Maps.newHashMap();
        Set<DID> unresolved = Sets.newTreeSet();
        getDeviceInfoMapLocally_(dids, resolved, unresolved);

        if (unresolved.isEmpty()) {
            return resolved;
        }

        if (updateLocalDeviceInfo_(Lists.newArrayList(unresolved))) {
            resolved.clear();
            getDeviceInfoMapLocally_(dids, resolved, unresolved);
        }

        return resolved;
    }

    /**
     * @return userid of the owner of the given {@code did}
     */
    public @Nullable UserID getDeviceOwnerNullable_(DID did) throws Exception
    {
        UserID owner = _d2u.getFromLocalNullable_(did);

        // SP call if local DB doesn't have the info
        if (owner == null && updateLocalDeviceInfo_(Lists.newArrayList(did))) {
            owner = _d2u.getFromLocalNullable_(did);
        }

        return owner;
    }

    /**
     * see {@link IUserAndDeviceNameDatabase#getDeviceNameNullable_}
     */
    public @Nullable String getDeviceNameNullable_(DID did) throws SQLException
    {
        return _udndb.getDeviceNameNullable_(did);
    }

    /**
     * see {@link IUserAndDeviceNameDatabase#getUserNameNullable_}
     */
    public @Nullable FullName getUserNameNullable_(UserID userId) throws SQLException
    {
        return _udndb.getUserNameNullable_(userId);
    }

    public void clearUserNameCache_()
            throws SQLException
    {
        _udndb.clearUserNameCache_();
    }

    public void clearDeviceNameCache_()
            throws SQLException
    {
        _udndb.clearDeviceNameCache_();
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
    public @Nonnull String getDisplayName_(DID did)
    {
        try {
            UserID owner = getDeviceOwnerNullable_(did);

            if (owner == null) return S.LBL_UNKNOWN_USER;
            else if (owner.equals(_localUser.get())) {
                String devicename = getDeviceNameNullable_(did);

                if (devicename == null) {
                    List<DID> unresolved = Collections.singletonList(did);
                    if (updateLocalDeviceInfo_(unresolved)) {
                        devicename = getDeviceNameNullable_(did);
                    }
                }

                return devicename == null ? S.LBL_UNKNOWN_DEVICE : devicename;
            } else {
                FullName username = getUserNameNullable_(owner);

                if (username == null) {
                    List<DID> unresolved = Collections.singletonList(did);
                    if (updateLocalDeviceInfo_(unresolved)) {
                        username = getUserNameNullable_(owner);
                    }
                }

                return username == null ? S.LBL_UNKNOWN_USER : username.toString();
            }
        } catch (Exception ex) {
            l.warn("Failed to lookup display name for {}", did, ex);
            return S.LBL_UNKNOWN_USER;
        }
    }
}
