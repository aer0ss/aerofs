package com.aerofs.daemon.core.acl;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.SubjectPermissions;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.store.IStoreJoiner;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;
import com.aerofs.proto.Common.PBSubjectPermissions;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Maps.newHashMapWithExpectedSize;

/**
 * ACL: Access Control List. AeroFS uses Discretionary ACL.
 *
 * This class is responsible for communicating with the central ACL database and the push service
 * to synchronize with the local ACL database (which is implemented by LocalACL)
 */
public class ACLSynchronizer
{
    private static final Logger l = Loggers.getLogger(ACLSynchronizer.class);

    private final TokenManager _tokenManager;
    private final TransManager _tm;
    private final ACLFilter _filter;
    private final LocalACL _lacl;
    private final IStoreJoiner _storeJoiner;
    private final IMapSIndex2SID _sidx2sid;
    private final IMapSID2SIndex _sid2sidx;
    private final CfgLocalUser _cfgLocalUser;
    private final SPBlockingClient.Factory _factSP;

    private class ServerACLReturn
    {
        final long  _serverEpoch;
        final Map<SID, IStoreJoiner.StoreInfo> _acl;

        private ServerACLReturn(long serverEpoch, Map<SID, IStoreJoiner.StoreInfo> acl)
        {
            _serverEpoch = serverEpoch;
            _acl = acl;
        }
    }

    @Inject
    public ACLSynchronizer(TokenManager tokenManager, TransManager tm, ACLFilter filter, LocalACL lacl,
            IStoreJoiner storeJoiner, IMapSIndex2SID sIndex2SID, IMapSID2SIndex sid2SIndex,
            CfgLocalUser cfgLocalUser, InjectableSPBlockingClientFactory factSP)
    {
        _tokenManager = tokenManager;
        _tm = tm;
        _filter = filter;
        _lacl = lacl;
        _storeJoiner = storeJoiner;
        _sidx2sid = sIndex2SID;
        _sid2sidx = sid2SIndex;
        _cfgLocalUser = cfgLocalUser;
        _factSP = factSP;
    }

    private void commitToLocal_(SIndex sidx, UserID userID, Permissions permissions)
            throws SQLException, ExNotFound
    {
        try (Trans t = _tm.begin_()) {
            _lacl.set_(sidx, Collections.singletonMap(userID, permissions), t);
            t.commit_();
        }
    }

    /**
     * Resolve the given SIndex to a SID
     * TODO: move this to a common location so other classes can use it too
     */
    private SID resolveSIndex_(SIndex sidx)
            throws SQLException
    {
        return _sidx2sid.getLocalOrAbsent_(sidx);
    }

    /**
     * Fetch ACL from the server and save to the local ACL. This method may block to communicate
     * with SP.
     */
    public void syncToLocal_() throws Exception
    {
        long localEpochBeforeSPCall = _filter.getEpoch_();
        updateACLFromSP_(localEpochBeforeSPCall);
    }

    /**
     * Fetch ACL from the server and save to the local ACL. This method may block to communicate
     * with SP.
     */
    public void syncToLocal_(long serverEpoch) throws Exception
    {
        long localEpochBeforeSPCall = _filter.getEpoch_();
        if (serverEpoch == localEpochBeforeSPCall) {
            l.info("acl notification ignored server epoch:{} local epoch:{}",
                    serverEpoch, localEpochBeforeSPCall);
            return;
        }
        updateACLFromSP_(localEpochBeforeSPCall);
    }

    /**
     * The most important role of this function is to ensure that ACL update never go backward as
     * it may cause inconsistent ACL state across clients and more importantly could result in data
     * loss if a shared folder is accidentally kicked out.
     *
     * Because ACL updates are *not incremental* we need to check that whatever epoch corresponds to
     * the result of the SP call is superior to the local epoch *after* the core lock is retaken
     * (RPC is done with core lock released).
     */
    private void updateACLFromSP_(long localEpochBeforeSPCall) throws Exception
    {
        ServerACLReturn serverACLReturn = getServerACL_(localEpochBeforeSPCall);

        // get the local acl again. we do this to verify that another acl update didn't sneak in
        // while we were parked
        //
        long localEpochAfterSPCall = _filter.getEpoch_();
        if (serverACLReturn._serverEpoch <= localEpochAfterSPCall) {
            l.info("server has no acl updates {} {} {}",
                    localEpochBeforeSPCall, localEpochAfterSPCall, serverACLReturn._serverEpoch);
            return;
        }

        Set<SIndex> stores = _lacl.getAccessibleStores_();

        l.info("accessible stores: {}", stores);

        // We go to great length to split ACL updates into multiple transactions. This increases
        // robustness and allows incremental progress to be made in the face of weird corner cases.
        //
        // Say you reinstall a Team Server w/ linked storage without cleanly unlinking. Upon
        // receiving ACLs, roots will be automatically created. If they are still at the default
        // location and tag files haven't been messed with they will be simply relinked (instead of
        // creating duplicates). All is fine and dandy until we attempt to adjust anchors
        // (see adjustAnchors_()): there may already be a physical object at the default location,
        // in which case lower layers will throw, expecting the linker/scanner to reconcile the
        // inconsistency by the time the operation is attempted again. Unfortunately the
        // linker/scanner would not have the opportunity to kick in since the user root store was
        // joined as part of the same transaction that attempted to adjust anchors and any exception
        // would rollback the auto-join. In such a scenario, using a single transaction would
        // prevent the reinstalled Team Server from ever applying ACL updates which would result in
        // a permanent no-sync.
        //
        // To safely allow incremental progress we must make sure that the ACL epoch is not bumped
        // when the ACL update is not fully applied.

        boolean updateEpoch = true;
        for (Entry<SID, IStoreJoiner.StoreInfo> e : serverACLReturn._acl.entrySet()) {
            SID sid = e.getKey();
            IStoreJoiner.StoreInfo info = e.getValue();
            try {
                SIndex sidx = updateACLAndJoin_(sid, info, stores);
                stores.remove(sidx);
            } catch (Exception ex) {
                // ignore errors to allow incremental progress but prevent epoch bump
                updateEpoch = false;
                l.warn("failed to update acl for {}", e.getKey(), ex);
            }
        }

        // Leave stores to which we no longer have access.
        // NB: Skip the leaving if any update/auto-join fails, since the set of stores to leave is
        // not computed correctly in this situation.
        if (updateEpoch) {
            for (SIndex sidx : stores) {
                updateEpoch &= leave_(sidx);
            }
        }

        if (updateEpoch) {
            for (Entry<SID, IStoreJoiner.StoreInfo> e : serverACLReturn._acl.entrySet()) {
                SIndex sidx = _sid2sidx.getNullable_(e.getKey());
                if (sidx != null && !e.getKey().isUserRoot()) {
                    updateEpoch &= onMembershipChange_(sidx, e.getValue());
                }
            }
        }

        if (updateEpoch) {
            updateEpoch_(serverACLReturn._serverEpoch);
        } else {
            throw new ExRetryLater("incomplete acl update");
        }
    }

    private SIndex updateACLAndJoin_(SID sid, IStoreJoiner.StoreInfo info, Set<SIndex> stores)
            throws Exception
    {
        Map<UserID, Permissions> roles = info._roles;
        l.debug("processing ACL: {} {} {} {}", sid, info._external, info._name, roles);

        // the local user should always be present in the ACL for each store in the reply
        if (!roles.containsKey(_cfgLocalUser.get())) {
            String msg = "Invalid ACL update " + roles;
            l.error(msg);
            throw new ExProtocolError(msg);
        }

        SIndex sidx = _sid2sidx.getLocalOrAbsentNullable_(sid);

        // avoid db work if ACL did not change
        if (sidx == null || !stores.contains(sidx) || !_lacl.get_(sidx).equals(roles)) {
            sidx = updateACLAndJoin_(sidx, sid, info, stores);
        }

        return sidx;
    }

    private SIndex updateACLAndJoin_(@Nullable SIndex sidx, SID sid, IStoreJoiner.StoreInfo info,
            Set<SIndex> stores)
            throws Exception
    {
        try (Trans t = _tm.begin_()) {
            if (sidx != null) {
                _lacl.clear_(sidx, t);
            } else {
                sidx = _sid2sidx.getAbsent_(sid, t);
            }

            _lacl.set_(sidx, info._roles, t);

            // TODO: handle changes of the external bit
            if (!stores.contains(sidx) && (_sidx2sid.getNullable_(sidx) == null)) {
                // not known and accessible: auto-join
                checkArgument(info._name != null, sid);
                _storeJoiner.joinStore_(sidx, sid, info, t);
            }

            t.commit_();
        }
        return sidx;
    }

    /**
     * @return whether the store was successfully left
     */
    private boolean leave_(SIndex sidx)
    {
        try (Trans t = _tm.begin_()) {
            _lacl.clear_(sidx, t);
            _storeJoiner.leaveStore_(sidx, _sidx2sid.getLocalOrAbsent_(sidx), t);
            t.commit_();
            return true;
        } catch (Exception e) {
            // ignore errors to allow incremental progress but prevent epoch bump
            l.warn("failed to leave store {}", sidx, e);
            return false;
        }
    }

    private boolean onMembershipChange_(SIndex sidx, IStoreJoiner.StoreInfo info)
    {
        try (Trans t = _tm.begin_()) {
            _storeJoiner.onMembershipChange_(sidx, info, t);
            return true;
        } catch (Exception e) {
            // ignore errors to allow incremental progress but prevent epoch bump
            l.warn("failed to react to membership change {} {}", sidx, info._name,
                    BaseLogUtil.suppress(e, ExRetryLater.class));
            return false;
        }
    }

    private void updateEpoch_(long serverEpoch) throws SQLException
    {
        try (Trans t = _tm.begin_()) {
            _filter.updateEpoch_(serverEpoch, t);
            t.commit_();
        }
    }

    private ServerACLReturn getServerACL_(long localEpoch)
            throws Exception
    {
        GetACLReply aclReply = _tokenManager.inPseudoPause_(Cat.UNLIMITED, "spacl",
                () -> _factSP.create().signInRemote().getACL(localEpoch));

        long serverEpoch = aclReply.getEpoch();
        Map<SID, IStoreJoiner.StoreInfo> stores = newHashMapWithExpectedSize(aclReply.getStoreAclCount());
        l.info("server return acl server epoch {} local epoch {}", serverEpoch, localEpoch);

        for (PBStoreACL store : aclReply.getStoreAclList()) {
            SID sid = new SID(BaseUtil.fromPB(store.getStoreId()));

            Set<UserID> externalMembers = Sets.newHashSet();
            ImmutableMap.Builder<UserID, Permissions> builder = ImmutableMap.builder();
            for (PBSubjectPermissions pbPair : store.getSubjectPermissionsList()) {
                SubjectPermissions srp = SubjectPermissions.fromPB(pbPair);

                // the core doesn't handle ACL subjects that are not UserIDs.
                if (!(srp._subject instanceof UserID)) {
                    l.warn("the server returned a non-UserID ACL subject, skipped: {}",
                            srp._subject);
                    continue;
                }

                UserID userID = (UserID)srp._subject;
                builder.put(userID, srp._permissions);
                if (pbPair.hasExternal() && pbPair.getExternal()) {
                    externalMembers.add(userID);
                }
            }

            IStoreJoiner.StoreInfo si = new IStoreJoiner.StoreInfo(store.getName(), store.getExternal(), builder.build(),
                    externalMembers);

            if (_filter.shouldKeep_(si._roles.keySet())) {
                stores.put(sid, si);
            } else {
                l.info("filter out {} {}", sid, si._roles.keySet());
            }
        }

        return new ServerACLReturn(serverEpoch, stores);
    }

    /**
     * Update ACLs via the SP.updateACL call
     */
    public void update_(SIndex sidx, UserID subject, Permissions permissions,
            boolean suppressSharingRulesWarnings)
            throws Exception
    {
        // first, resolve the sid and prepare data for protobuf
        SID sid = resolveSIndex_(sidx);

        // make the SP call (done before adding entries to the local database to avoid changing the
        // local database if the SP call fails)
        _tokenManager.inPseudoPause_(Cat.UNLIMITED, "spacl", () -> _factSP.create()
                        .signInRemote()
                        .updateACL(BaseUtil.toPB(sid), subject.getString(), permissions.toPB(),
                                suppressSharingRulesWarnings));

        // add new entries to the local database
        commitToLocal_(sidx, subject, permissions);
    }

    public void delete_(SIndex sidx, UserID subject)
            throws Exception
    {
        //
        // call SP first to avoid setting local ACL if SP returns no permission or other errors.
        //

        SID sid = resolveSIndex_(sidx);

        // make the SP call

        _tokenManager.inPseudoPause_(Cat.UNLIMITED, "spacl",
                () -> _factSP.create().signInRemote().deleteACL(BaseUtil.toPB(sid), subject.getString()));

        //
        // for faster UI refresh and banning the removed users, immediately add the entries to
        // the local database rather than waiting for notifications from the push service.
        //

        try (Trans t = _tm.begin_()) {
            _lacl.delete_(sidx, subject, t);
            t.commit_();
        }
    }
}
