/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.alias.Aliasing;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.migration.IEmigrantDetector;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.core.store.StoreCreator.Conversion;
import com.aerofs.daemon.core.transfers.download.ExUnsolvedMetaMetaConflict;
import com.aerofs.daemon.core.transfers.download.IDownloadContext;
import com.aerofs.daemon.core.transfers.download.dependence.DependencyEdge.DependencyType;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.lib.exception.ExDependsOn;
import com.aerofs.daemon.lib.exception.ExNameConflictDependsOn;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.OCID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Core.PBGetComponentResponse;
import com.aerofs.proto.Core.PBMeta;
import com.google.common.base.Joiner;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;

import static com.aerofs.daemon.core.protocol.GetComponentResponse.fromPB;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * This class is responsible for handling GetComponentResponse messages for META components
 * and updating the local DB/fs to match the state on the remote peer.
 *
 * TODO: split in 2+ classes (decision/action at least, similar to MC/MCO split)
 */
public class MetaUpdater
{
    private final static Logger l = Loggers.getLogger(MetaUpdater.class);

    private TransManager _tm;
    private DirectoryService _ds;
    private MetaDiff _mdiff;
    private Aliasing _al;
    private MapAlias2Target _a2t;
    private LocalACL _lacl;
    private IEmigrantDetector _emd;
    private NativeVersionControl _nvc;
    private ObjectMover _om;
    private ObjectCreator _oc;
    private StoreCreator _sc;
    private VersionUpdater _vu;

    @Inject
    public void inject_(TransManager tm, DirectoryService ds, NativeVersionControl nvc, MetaDiff mdiff,
            Aliasing al, MapAlias2Target a2t, LocalACL lacl, IEmigrantDetector emd,
            ObjectCreator oc, ObjectMover om, StoreCreator sc, VersionUpdater vu)
    {
        _tm = tm;
        _ds = ds;
        _mdiff = mdiff;
        _al = al;
        _a2t = a2t;
        _lacl = lacl;
        _emd = emd;
        _nvc = nvc;
        _oc = oc;
        _om = om;
        _sc = sc;
        _vu = vu;
    }


    void processMetaResponse_(SOCID socid, DigestedMessage msg, IDownloadContext cxt)
            throws Exception
    {
        checkState(socid.cid().isMeta());
        final PBGetComponentResponse pbResponse = msg.pb().getGetComponentResponse();

        OID oidParent;
        int metaDiff;

        PBMeta meta = pbResponse.getMeta();
        oidParent = new OID(meta.getParentObjectId());

        // "Dereference" the parent OID if it has been aliased locally, otherwise:
        // *  the parent's OA is not present (aliased objects don't have
        //    corresponding OAs), so later code will throw ExDependsOn, causing the download
        //    subsystem to attempt the parent from the remote peer.
        // *  when the local peer receives the parent object, because the parent has been
        //    aliased, the local peer ignores the message and return success to the download
        //    subsystem.
        // *  after the dependency is incorrectly "resolved", the download subsystem attempts
        //    the original object, causing step 1 to repeat.
        // as a result, the system would enter an infinite loop.
        OID derefParent = _a2t.dereferenceAliasedOID_(new SOID(socid.sidx(), oidParent)).oid();
        if (!derefParent.equals(oidParent)) l.info("deref {} -> {}", oidParent, derefParent);
        oidParent = derefParent;

        // We don't gracefully handle the parent OID being the same as that sent in the msg
        if (oidParent.equals(socid.oid())) {
            l.error("{} cycle: {}", msg.did(), oidParent);
            throw new ExProtocolError("cycle " + oidParent);
        }
        assert !oidParent.equals(socid.oid()) : "p msg " + oidParent + " socid " + socid;

        metaDiff = _mdiff.computeMetaDiff_(socid.soid(), meta, oidParent);

        if (Util.test(metaDiff, MetaDiff.NAME | MetaDiff.PARENT)) {
            // perform emigration only for the target object as oppose to the aliased object,
            // because at this point it's difficult to decide whether an object, and which one,
            // will be aliased or renamed, etc. this is all right as very rare that
            // aliasing/name conflicts and emigration happen at the same time.
            _emd.detectAndPerformEmigration_(socid.soid(), oidParent, meta.getName(),
                    meta.getEmigrantTargetAncestorSidList(), cxt);
            // N.B after the call the local meta might have been updated.
            // but we don't need to update metaDiff.
        }

        if (meta.hasTargetOid()) {
            // Process the alias message.
            assert meta.hasTargetVersion();
            _al.processAliasMsg_(
                    socid.soid(),                                           // alias
                    Version.fromPB(pbResponse.getVersion()),                // vRemoteAlias
                    new SOID(socid.sidx(), new OID(meta.getTargetOid())),   // target
                    Version.fromPB(meta.getTargetVersion()),                // vRemoteTarget
                    oidParent,
                    metaDiff, meta, cxt);

            // processAliasMsg_() does all the processing necessary for alias msg hence
            // return from this point.
            return;
        } else {
            // Process non-alias message.
            OID targetOIDLocal = _a2t.getNullable_(socid.soid());
            if (targetOIDLocal != null) {
                _al.processNonAliasMsgOnLocallyAliasedObject_(socid, targetOIDLocal);
                // the above method does the necessary processing for update on a locally
                // aliased object hence return from this point.
                return;
            } else {
                l.debug("meta diff: " + String.format("0x%1$x", metaDiff));
                // see Rule 2 in acl.md
                if (metaDiff != 0 && !_lacl.check_(msg.user(), socid.sidx(), Permissions.EDITOR)) {
                    l.warn("{} on {} has no editor perm for {}", msg.user(), msg.ep(), socid.sidx());
                    throw new ExSenderHasNoPerm();
                }
            }
        }

        /////////////////////////////////////////
        // determine causal relation

        Version vRemote = Version.fromPB(pbResponse.getVersion());
        CausalityResult cr = computeCausality_(socid.soid(), vRemote, metaDiff);

        if (cr == null) return;

        // This is the branch to which the update should be applied, as determined by
        // ReceiveAndApplyUpdate#computeCausalityForMeta_
        SOCKID targetBranch = new SOCKID(socid, cr._kidx);

        /////////////////////////////////////////
        // apply update

        // N.B. vLocal.isZero_() doesn't mean the component is new to us.
        // It may be the case that it's not new but all the local ticks
        // have been replaced by remote ticks.

        final boolean wasPresent = _ds.isPresent_(targetBranch);

        Throwable rollbackCause = null;

        Trans t = _tm.begin_();
        try {
            if (metaDiff != 0) {
                boolean oidsAliasedOnNameConflict = applyMeta_(socid.soid(),
                        pbResponse.getMeta(), oidParent,
                        wasPresent, metaDiff, t,
                        // for non-alias message create a new version
                        // on aliasing name conflict.
                        null, vRemote, socid.soid(), cr, cxt);

                // Aliasing objects on name conflicts updates versions and bunch
                // of stuff (see resolveNameConflictOnNewRemoteObjectByAliasing_()). No further
                // processing is required hence return from this point.
                if (oidsAliasedOnNameConflict) {
                    t.commit_();
                    return;
                }
            }

            updateVersion_(targetBranch, vRemote, cr, t);

            t.commit_();
            l.info("{} ok {}", msg.ep(), socid);

        } catch (Exception | Error e) {
            rollbackCause = e;
            throw e;
        } finally {
            t.end_(rollbackCause);
        }
    }

    public static class CausalityResult
    {
        // the kidx to which the downloaded update will be applied
        public final KIndex _kidx;
        // the version vector to be added to the branch corresponding to kidxApply
        public final Version _vAddLocal;
        // if a true conflict (vs. false conflict) is to be merged
        public final boolean _incrementVersion;

        // if the remote object was renamed to resolve a conflict (increment its version)
        public boolean _conflictRename;

        public final Version _vLocal;

        CausalityResult(Version vAddLocal, Version vLocal)
        {
            this(vAddLocal, false, vLocal);
        }

        CausalityResult(@Nonnull Version vAddLocal,
                boolean incrementVersion, @Nonnull Version vLocal)
        {
            _kidx = KIndex.MASTER;
            _vAddLocal = vAddLocal;
            _incrementVersion = incrementVersion;
            _vLocal = vLocal;
            _conflictRename = false;
        }

        @Override
        public String toString()
        {
            return Joiner.on(' ').useForNull("null").join(_kidx, _vAddLocal, _incrementVersion,
                    _conflictRename, _vLocal);
        }
    }

    /**
     * Deterministically compare two conflicting versions using the difference of the two versions.
     * The larger one wins. We used to use meta data change time before but it's not reliable
     * because it may change when the meta is transferred from one peer to another.
     */
    private static int compareLargestDIDsInVersions(Version v1, Version v2)
    {
        assert !v1.isZero_();
        assert !v2.isZero_();
        DID did1 = checkNotNull(v1.findLargestDID());
        DID did2 = checkNotNull(v2.findLargestDID());
        return did1.compareTo(did2);
    }

    /**
     * @return null if not to apply the update
     */
    public @Nullable
    CausalityResult computeCausality_(SOID soid, Version vRemote, int metaDiff)
            throws SQLException, ExUnsolvedMetaMetaConflict
    {
        SOCKID k = new SOCKID(soid, CID.META, KIndex.MASTER);
        final Version vLocal = _nvc.getLocalVersion_(k);
        Version vR_L = vRemote.sub_(vLocal);
        Version vL_R = vLocal.sub_(vRemote);

        if (l.isDebugEnabled()) l.debug(k + " l " + vLocal);

        if (vR_L.isZero_()) {
            if (_ds.isPresent_(k) || !vL_R.isZero_()) {
                // don't apply if it doesn't correspond to an accept_equality
                // call to fetch in an off-cache file.
                // c.c().isOnline_(k) || !vL_R.isZero_(). TODO fix it
                // for dl'ing a conflict branch when master is absent?
                //
                // computeCausalityForContent() has the same logic
                l.warn("in cache or l - r > 0");
                return null;
            } else {
                return new CausalityResult(vR_L, vLocal);
            }
        } else if (vL_R.isZero_()) {
            return new CausalityResult(vR_L, vLocal);
        }

        // all non-conflict cases have been handled above. now it's a conflict

        if (metaDiff == 0) {
            l.debug("merge false meta conflict");
            return new CausalityResult(vR_L, vLocal);
        } else {
            // TODO forbidden meta should always win

            int comp = compareLargestDIDsInVersions(vR_L, vL_R);
            assert comp != 0;
            if (comp > 0) {
                // TODO: throw to prevent meta/meta conflicts from being ignored when aliasing?
                l.warn("true meta conflict on {}. {} > {}. don't apply", soid, vLocal, vRemote);
                throw new ExUnsolvedMetaMetaConflict();
            } else {
                l.debug("true meta conflict. l < r. merge");
                return new CausalityResult(vR_L, true, vLocal);
            }
        }
    }

    /**
     * @param oidParent is assumed to be a target object (i.e. not in the alias table)
     * @return true if a name conflict was detected and oids were aliased.
     * TODO (MJ) there should be only one source of the SIndex of interest,
     * but right now it can be acquired from soid, noNewVersion, and soidMsg. The latter two
     * should be changed to OID types.
     */
    public boolean applyMeta_(SOID soid, PBMeta meta, OID oidParent,
            final boolean wasPresent, int metaDiff, Trans t, @Nullable SOID noNewVersion,
            Version vRemote, final SOID soidMsg, CausalityResult cr, IDownloadContext cxt)
            throws Exception
    {
        final SOID soidParent = new SOID(soid.sidx(), oidParent);

        // Neither the OID of interest nor the OID for the parent should be an aliased object
        // at this point. They should have been dereferenced in GetComponentReply
        assert !_a2t.isAliased_(soidParent) : soidParent;
        assert !_a2t.isAliased_(soid) : soid;

        assert !soid.equals(soidParent) : soid;

        // The parent must exist locally, otherwise this SOID depends on the parent
        if (!_ds.hasOA_(soidParent)) {
            throw new ExDependsOn(new OCID(oidParent, CID.META), DependencyType.PARENT);
        }

        try {
            // N.B. the statement that may throw ExExist must be the first in
            // this try block, as resolveNameConflict_ assumes that no
            // metadata change has been written to the persistent store.
            //
            if (!wasPresent) {
                // the root folder must have been created at store creation
                assert !soid.oid().isRoot();

                assert noNewVersion != null || Util.test(metaDiff, MetaDiff.PARENT | MetaDiff.NAME);

                _oc.createMeta_(fromPB(meta.getType()), soid, oidParent, meta.getName(),
                        PhysicalOp.APPLY, true, false, t);

            } else {
                if (Util.test(metaDiff, MetaDiff.PARENT | MetaDiff.NAME)) {

                    resolveParentConflictIfRemoteParentIsLocallyNestedUnderChild_(soid.sidx(),
                            soid.oid(), oidParent, t);

                    _om.moveInSameStore_(soid, oidParent, meta.getName(), PhysicalOp.APPLY,
                            false, t);
                }
            }
        } catch (ExAlreadyExist e) {
            l.warn("name conflict {} in {}", soid, cxt);
            return resolveNameConflict_(soid, oidParent, meta, wasPresent, metaDiff, t,
                    noNewVersion, vRemote, soidMsg, cr, cxt);
        } catch (Exception e) {
            l.error("failed to apply meta update", e);
            throw e;
        }

        return false;
    }

    private void resolveParentConflictIfRemoteParentIsLocallyNestedUnderChild_(SIndex sidx,
            OID child, OID remoteParent, Trans t)
            throws Exception
    {
        SOID soidRemoteParent = new SOID(sidx, remoteParent);
        OA oaChild = _ds.getOA_(new SOID(sidx, child));

        Path pathRemoteParent = _ds.resolve_(soidRemoteParent);
        Path pathChild = _ds.resolve_(oaChild);

        if (pathRemoteParent.isStrictlyUnder(pathChild)) {
            // A cyclic dependency would result if we tried to apply this update.
            // The current approach to resolve this conflict is to move the to-be parent object
            // under the child's current parent. It's not beautiful but works and will reach
            // consistency  across devices.
            l.debug("resolve remote parent is locally nested under child " + child + " "
                    + remoteParent);

            // Avoid a local name conflict in the new path of the remote parent object
            String newRemoteParentName = _ds.generateConflictFreeFileName_(
                    pathChild.removeLast(), pathRemoteParent.last());

            _om.moveInSameStore_(soidRemoteParent, oaChild.parent(), newRemoteParentName,
                    PhysicalOp.APPLY, true, t);
        }
    }


    /**
     *  Resolves name conflict either by aliasing if received object wasn't
     *  present or by renaming one of the conflicting objects.
     * @param soidRemote soid of the remote object being received.
     * @param parent parent of the soid being received.
     * @param soidNoNewVersion On resolving name conflict by aliasing, don't
     *        generate a new version for the alias if alias soid matches
     *        soidNoNewVersion.
     * @param soidMsg soid of the object for which GetComponentCall was made.
     *        It may not necessarily be same as soidRemote especially while
     *        processing alias msg. It's used for detecting cyclic dependency.
     * @return whether OIDs were merged (i.e. one is aliased to the other)
     */
    private boolean resolveNameConflict_(SOID soidRemote, OID parent, PBMeta meta,
            boolean wasPresent, int metaDiff, Trans t, @Nullable SOID soidNoNewVersion, Version vRemote,
            final SOID soidMsg, CausalityResult cr, IDownloadContext cxt)
            throws Exception
    {
        Path pParent = _ds.resolve_(new SOID(soidRemote.sidx(), parent));

        Path pLocal = pParent.append(meta.getName());
        SOID soidLocal = _ds.resolveNullable_(pLocal);
        checkArgument(soidLocal != null && soidLocal.sidx().equals(soidRemote.sidx()), "%s %s %s",
                soidLocal, soidRemote, pLocal);
        OA oaLocal = _ds.getOA_(soidLocal);
        OA.Type typeRemote = fromPB(meta.getType());

        l.info("name conflict on {}: local {} {} remote {} {}", pLocal, soidLocal.oid(),
                oaLocal.type(), soidRemote.oid(), typeRemote);

        Conversion conversion = _sc.detectFolderToAnchorConversion_(soidLocal.oid(), oaLocal.type(),
                soidRemote.oid(), typeRemote);

        if (conversion != Conversion.NONE) {
            // race conditions FTW
            //
            // It is possible to receive download the META for an anchor before
            // receiving the META update (deletion) for the corresponding dir.
            //
            // It is also possible to receive META for the not-yet-deleted dir
            // while the anchor is already there.
            //
            // In both cases we want to avoid spurious renames that the regular
            // conflict resolution algorithm would cause. Ideally we'd just
            // trigger local migration but that's more complicated that it sounds
            // so for now we simply temporarily rename the directory to make
            // room for the anchor.
            //
            // We make sure not to bump the version of the dir when doing this
            // rename to reduce the likelihood of it spreading to other nodes
            // and more importantly to prevent it from persisting (the deletion
            // has a higher META version and will eventually reach all nodes).
            l.info("folder->anchor conversion detected: {}{}{}", soidLocal,
                    conversion == Conversion.REMOTE_ANCHOR ? "->" : "<-", soidRemote);

            // TODO: avoid this ugly rename, perform local migration instead
            String newName = oaLocal.name() + " (being converted to shared folder - do not remove)";

            while (_ds.resolveNullable_(pParent.append(newName)) != null) {
                newName = Util.nextFileName(newName);
            }

            if (conversion == Conversion.REMOTE_ANCHOR) {
                _om.moveInSameStore_(soidLocal, parent, newName, PhysicalOp.APPLY, false, t);
            } else {
                checkState(conversion == Conversion.LOCAL_ANCHOR);
                meta = PBMeta.newBuilder(meta).setName(newName).build();
            }
            applyMeta_(soidRemote, meta, parent, wasPresent, metaDiff, t, soidNoNewVersion,
                    vRemote, soidMsg, cr, cxt);
            return false;
        }

        // Detect and declare a dependency if
        // 1) the local logical object for the msg's file name doesn't match the remote
        // 2) new updates about the local logical object haven't already been requested
        if (!soidLocal.equals(soidMsg)) {
            // N.B. We are assuming soidMsg == Download._k.soid().
            // TODO (MJ) this isn't very good design and should be addressed. It is currently
            // possible for a developer to break this assumption very easily in separate classes
            // than this one. Lets find a way to avoid breaking the assumption
            if (!cxt.hasResolved_(new SOCID(soidLocal, CID.META))) {
                throw new ExNameConflictDependsOn(soidLocal.oid(), parent, vRemote, meta,
                        soidMsg);
            }
        }

        // Either cyclic dependency or local object already sync'ed
        l.debug("true name conflict");

        // Resolve this name conflict by aliasing only if
        // 1) the remote object is not present locally,
        // 2) the remote object is not an anchor,
        // and 3) if the local and remote types of the object are equivalent
        // TODO: 4) if the local object has ticks for the local device?
        if (!wasPresent && meta.getType() != PBMeta.Type.ANCHOR && typeRemote == oaLocal.type()) {
            // Resolving name conflict by aliasing the oids.
            _al.resolveNameConflictOnNewRemoteObjectByAliasing_(soidRemote, soidLocal,
                    vRemote, soidNoNewVersion, t);
            return true;
        } else {
            resolveNameConflictByRenaming_(soidRemote, soidLocal, wasPresent, parent, pParent,
                    vRemote, meta, metaDiff, soidMsg, cr, cxt, t);
            return false;
        }
    }

    public void resolveNameConflictByRenaming_(SOID soidRemote, SOID soidLocal,
            boolean wasPresent, OID parent, Path pParent, Version vRemote, PBMeta meta,
            int metaDiff, SOID soidMsg, CausalityResult cr, IDownloadContext cxt, Trans t)
            throws Exception
    {
        // Resolve name conflict by generating a new name.
        l.debug("Resolving name conflicts by renaming one of the oid.");

        int comp = soidLocal.compareTo(soidRemote);
        assert comp != 0;
        String newName = _ds.generateConflictFreeFileName_(pParent, meta.getName());
        assert !newName.equals(meta.getName());

        if (comp > 0) {
            // local wins
            l.debug("change remote name");
            PBMeta newMeta = PBMeta.newBuilder().mergeFrom(meta).setName(newName).build();
            applyMeta_(soidRemote, newMeta, parent, wasPresent, metaDiff, t, null,
                    vRemote, soidMsg, cr, cxt);

            // The version for soidRemote should be incremented, (with VersionUpdater.update_)
            // Unfortunately that can't be done here, as applyUpdateMetaAndContent will detect
            // that the version changed during the application of the update, and throw ExAborted,
            // effectively making this code path a no-op. Instead, set cr._conflictRename to true
            // so that applyUpdateMetaAndContent will increment the version for us.
            cr._conflictRename = true;
        } else {
            // remote wins
            l.debug("change local name");
            _om.moveInSameStore_(soidLocal, parent, newName, PhysicalOp.APPLY, true, t);
            applyMeta_(soidRemote, meta, parent, wasPresent, metaDiff, t, null,
                    vRemote, soidMsg, cr, cxt);
        }
    }

    /**
     * update version vectors
     */
    public void updateVersion_(SOCKID k, Version vRemote, CausalityResult res, Trans t)
            throws SQLException, IOException, ExNotFound, ExAborted
    {
        Version vKML = _nvc.getKMLVersion_(k.socid());
        Version vKML_R = vKML.sub_(vRemote);
        Version vDelKML = vKML.sub_(vKML_R);

        l.debug("{}: r {}  kml {} -kml {} +l {}", k, vRemote, vKML, vDelKML, res._vAddLocal);

        // check if the local version has changed during our pauses
        if (!_nvc.getLocalVersion_(k).isDominatedBy_(res._vLocal)) {
            throw new ExAborted(k + " version changed locally.");
        }

        // update version vectors
        _nvc.deleteKMLVersion_(k.socid(), vDelKML, t);
        _nvc.addLocalVersion_(k, res._vAddLocal, t);

        // increment the version if
        // 1) this update was a merge of true conflicts OR
        // 2) the object in the msg was renamed to resolve a local name conflict
        if (res._incrementVersion || res._conflictRename) {
            _vu.update_(k, t);
        }
    }
}
