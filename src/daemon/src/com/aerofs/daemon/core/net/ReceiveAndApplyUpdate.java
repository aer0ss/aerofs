/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.alias.Aliasing;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.net.dependence.DependencyEdge.DependencyType;
import com.aerofs.daemon.core.net.proto.ComputeHashCall;
import com.aerofs.daemon.core.net.proto.MetaDiff;
import com.aerofs.daemon.core.net.proto.PrefixVersionControl;
import com.aerofs.daemon.core.object.BranchDeleter;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.lib.exception.ExDependsOn;
import com.aerofs.daemon.lib.exception.ExNameConflictDependsOn;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.l.L;
import com.aerofs.lib.*;
import com.aerofs.lib.FileUtil.FileName;
import com.aerofs.lib.analytics.Analytics;
import com.aerofs.lib.ex.*;
import com.aerofs.lib.id.*;
import com.aerofs.proto.Core.PBGetComReply;
import com.aerofs.proto.Core.PBMeta;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.aerofs.daemon.core.net.proto.GetComponentReply.*;

public class ReceiveAndApplyUpdate
{
    private static final Logger l = Util.l(ReceiveAndApplyUpdate.class);

    private DirectoryService _ds;
    private PrefixVersionControl _pvc;
    private NativeVersionControl _nvc;
    private Hasher _hasher;
    private VersionUpdater _vu;
    private ObjectCreator _oc;
    private ObjectMover _om;
    private IPhysicalStorage _ps;
    private DownloadState _dlState;
    private ComputeHashCall _computeHashCall;
    private StoreCreator _sc;
    private IncomingStreams _iss;
    private Metrics _m;
    private Aliasing _al;
    private MapAlias2Target _a2t;
    private BranchDeleter _bd;
    private TransManager _tm;
    private MapSIndex2Store _sidx2s;
    private Analytics _a;

    @Inject
    public void inject_(DirectoryService ds, PrefixVersionControl pvc, NativeVersionControl nvc,
            Hasher hasher, VersionUpdater vu, ObjectCreator oc, ObjectMover om,
            IPhysicalStorage ps, DownloadState dlState, ComputeHashCall computeHashCall, StoreCreator sc,
            IncomingStreams iss, Metrics m, Aliasing al, BranchDeleter bd, TransManager tm,
            MapSIndex2Store sidx2s, MapAlias2Target alias2target, Analytics a)
    {
        _ds = ds;
        _pvc = pvc;
        _nvc = nvc;
        _hasher = hasher;
        _vu = vu;
        _oc = oc;
        _om = om;
        _ps = ps;
        _dlState = dlState;
        _computeHashCall = computeHashCall;
        _sc = sc;
        _iss = iss;
        _m = m;
        _al = al;
        _bd = bd;
        _tm = tm;
        _sidx2s = sidx2s;
        _a2t = alias2target;
        _a = a;
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
        // the branches to be deleted. never be null
        public final Collection<KIndex> _kidcsDel;

        @Nullable public final ContentHash _hash;

        // TODO (MJ) this shouldn't be Nullable as there are users of this Version that expect it
        // to be non-null (e.g. Version.sub_). Unfortunately some callers of the constructor
        // pass in a null object for unknown reasons (specifically in computeCausalityForContent_)
        @Nullable public final Version _vLocal;

        CausalityResult(KIndex kidx, Version vAddLocal, Version vLocal)
        {
            this(kidx, vAddLocal, null, false, null, vLocal);
        }

        CausalityResult(KIndex kidx, Version vAddLocal,
            @Nullable Collection<KIndex> kidcsDel, boolean incrementVersion,
            @Nullable ContentHash h, @Nullable Version vLocal)
        {
            _kidx = kidx;
            _vAddLocal = vAddLocal;
            _incrementVersion = incrementVersion;
            if (kidcsDel == null) _kidcsDel = Collections.emptyList();
            else _kidcsDel = kidcsDel;
            _hash = h;
            _vLocal = vLocal;
            _conflictRename = false;
        }

        @Override
        public String toString()
        {
            return Joiner.on(' ').useForNull("null").join(_kidx, _vAddLocal, _incrementVersion,
                    _conflictRename, _kidcsDel, _hash, _vLocal);
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
        DID did1 = v1.findLargestDID();
        DID did2 = v2.findLargestDID();
        assert did1 != null;
        assert did2 != null;
        return did1.compareTo(did2);
    }

    /**
     * @return null if not to apply the update
     */
    public @Nullable CausalityResult computeCausalityForMeta_(SOID soid, Version vRemote,
        int metaDiff) throws SQLException
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
                return new CausalityResult(KIndex.MASTER, vR_L, vLocal);
            }
        } else if (vL_R.isZero_()) {
            return new CausalityResult(KIndex.MASTER, vR_L, vLocal);
        }

        // all non-conflict cases have been handled above. now it's a conflict

        if (metaDiff == 0) {
            l.debug("merge false meta conflict");
            return new CausalityResult(KIndex.MASTER, vR_L, vLocal);
        } else {
            // TODO forbidden meta should always win

            int comp = compareLargestDIDsInVersions(vR_L, vL_R);
            assert comp != 0;
            if (comp > 0) {
                l.debug("true meta conflict. l > r. don't apply");
                return null;
            } else {
                l.debug("true meta conflict. l < r. merge");
                return new CausalityResult(KIndex.MASTER, vR_L, null, true, null, vLocal);
            }
        }
    }

    private boolean isContentSame_(SOCKID k, @Nonnull ContentHash hRemote, Token tk)
            throws DigestException, ExAborted, IOException, ExNotFound, SQLException
    {
        ContentHash hLocal = _hasher.computeHash_(k.sokid(), false, tk);
        return isContentSame(k, hLocal, hRemote);
    }

    private static boolean isContentSame(SOCKID k, @Nonnull ContentHash hLocal,
            @Nonnull ContentHash hRemote)
    {
        if (l.isDebugEnabled()) {
            l.debug("Local hash: " + hLocal.toHex() + " Remote hash:" + hRemote.toHex());
        }
        boolean result = hRemote.equals(hLocal);
        if (l.isDebugEnabled()) {
            l.debug("Comparing hashes: " + result + " " + k);
        }
        return result;
    }


    /**
     * @return null if not to apply the update
     */
    public @Nullable CausalityResult computeCausalityForContent_(SOID soid,
            Version vRemote, DigestedMessage msg, Token tk)
            throws Exception
    {
        Version vAddLocal = new Version().add_(vRemote);
        KIndex kidxApply = null;
        Version vApply = null;
        int kidxMax = KIndex.MASTER.getInt() - 1;
        List<KIndex> kidcsDel = Lists.newArrayList();
        PBGetComReply reply = msg.pb().getGetComReply();
        ContentHash hRemote = null;
        if (reply.hasHashLength()) {
            int hashBytesRead = 0;
            int hashBytesTotal = reply.getHashLength();
            // If the protobuf, hash, and file content all fit into a single datagram,
            // then the hash and file content will be found in msg.is().
            // If not, then the hash and file content (if present) will be streamed.
            if (msg.streamKey() != null) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                while (hashBytesRead < hashBytesTotal) {
                    // This code assumes that the hash and following data will arrive in separate
                    // chunks.  While this is true now, it may not be once the underlying layers
                    // are allowed to fragment/reassemble.
                    ByteArrayInputStream is = _iss.recvChunk_(msg.streamKey(), tk);
                    try {
                        hashBytesRead += copyOneChunk(is, os);
                    } finally {
                        is.close();
                    }
                    l.debug("Read " + hashBytesRead + " hash bytes of " + hashBytesTotal);
                }
                hRemote = new ContentHash(os.toByteArray());
            } else {
                DataInputStream is = new DataInputStream(msg.is());
                byte[] hashBuf = new byte[hashBytesTotal];
                is.readFully(hashBuf);
                // N.B. don't close is - it'll close msg.is(), which will keep us from reading
                // the file content after the hash.
                hRemote = new ContentHash(hashBuf);
            }
        }

        // MASTER branch should be considered first for application of update as opposed
        // to conflict branches when possible (see "@@" below).
        // Hence iterate over ordered KIndices.
        for (KIndex kidx : _ds.getOAThrows_(soid).cas().keySet()) {
            SOCKID kBranch = new SOCKID(soid, CID.CONTENT, kidx);
            Version vBranch = _nvc.getLocalVersion_(kBranch);
            kidxMax = Math.max(kidx.getInt(), kidxMax);

            if (l.isDebugEnabled()) l.debug(kBranch + " l " + vBranch);

            if (vRemote.sub_(vBranch).isZero_()) {
                if (_ds.isPresent_(kBranch) || !vBranch.sub_(vRemote).isZero_()) {
                    // see computCausalityForMeta()
                    l.warn("in cache or l - r > 0");
                    return null;

                } else {
                    // vRemote == vLocal
                    kidxApply = kidx;
                    vApply = vBranch;
                    vAddLocal = new Version();
                }

            } else  {
                // Computing/requesting hash is expensive so we compute it only
                // when necessary and ensure hash of remote branch is computed
                // only once.
                final boolean isRemoteDominating = vBranch.sub_(vRemote).isZero_();
                if (!isRemoteDominating && hRemote == null) {
                    l.debug("Fetching hash on demand");
                    // Abort the ongoing transfer.  If we don't, the peer will continue sending
                    // file content which we will queue until we exhaust the heap.
                    if (msg.streamKey() != null) {
                        _iss.end_(msg.streamKey());
                    }
                    // Compute the local ContentHash first.  This ensures that the local ContentHash
                    // is available by the next time we try to download this component.
                    _hasher.computeHash_(kBranch.sokid(), false, tk);
                    // Send a ComputeHashCall to make the remote peer also compute the ContentHash.
                    // This will block for a while until the remote peer computes the hash.
                    _computeHashCall.rpc_(soid, vRemote, msg.did(), tk);
                    // Once the above call returns, throw so Downloads will restart this Download.
                    // The next time through, the peer should send the hash. Since we already
                    // computed the local hash, we can compare them.
                    throw new ExRestartWithHashComputed("restart dl after hash");
                }

                if (isRemoteDominating || isContentSame_(kBranch, hRemote, tk)) {
                    if (kidxApply == null) {
                        // @@ see comments above
                        kidxApply = kidx;
                        vApply = vBranch;
                        vAddLocal = vAddLocal.sub_(vBranch);
                    } else {
                        kidcsDel.add(kidx);
                        vAddLocal = vAddLocal.add_(vBranch);
                    }
                } else {
                    // it's a conflict. do nothing but move on to the next branch
                }
            }

            if (l.isDebugEnabled()) {
                l.debug("kidx: " + kidx.getInt() + " vAddLocal: " + vAddLocal);
            }
        }

        // no subordinate branch was found. create a new branch
        if (kidxApply == null)  {
            kidxApply = new KIndex(kidxMax + 1);
        } else {
            // It's necessary to compute diff of the vector being added
            // from the branch to which vector will be applied.
            vAddLocal = vAddLocal.sub_(vApply);
        }

        if (l.isDebugEnabled()) {
            l.debug("Final vAddLocal: " + vAddLocal + " kApply: " + kidxApply);
        }
        return new CausalityResult(kidxApply, vAddLocal, kidcsDel, false,
            hRemote, vApply);
    }

    /**
     * @param oidParent is assumed to be a target object (i.e. not in the alias table)
     * @return true if a name conflict was detected and oids were aliased.
     * TODO (MJ) there should be only one source of the SIndex of interest,
     * but right now it can be acquired from soid, noNewVersion, and soidMsg. The latter two
     * should be changed to OID types.
     */
    public boolean applyMeta_(DID did, SOID soid, PBMeta meta, OID oidParent,
            final boolean wasPresent, int metaDiff, Trans t, @Nullable SOID noNewVersion,
            Version vRemote, final SOID soidMsg, Set<OCID> requested, CausalityResult cr)
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
            throw new ExDependsOn(new OCID(oidParent, CID.META), did, DependencyType.PARENT, false);
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
                        meta.getFlags(), PhysicalOp.APPLY, true, false, t);

            } else {
                if (Util.test(metaDiff, MetaDiff.PARENT | MetaDiff.NAME)) {
                    _om.moveInSameStore_(soid, oidParent, meta.getName(), PhysicalOp.APPLY, false,
                            false, t);
                }
            }
        } catch (ExAlreadyExist e) {
            l.warn(Util.e(e));
            return resolveNameConflict_(did, soid, oidParent, meta, wasPresent, metaDiff, t,
                    noNewVersion, vRemote, soidMsg, requested, cr);
        } catch (ExNotDir e) {
            SystemUtil.fatal(e);
        }

        return false;
    }

    /**
     *  Resolves name conflict either by aliasing if received object wasn't
     *  present or by renaming one of the conflicting objects.
     * @param did device ID
     * @param soidRemote soid of the remote object being received.
     * @param parent parent of the soid being received.
     * @param soidNoNewVersion On resolving name conflict by aliasing, don't
     *        generate a new version for the alias if alias soid matches
     *        soidNoNewVersion.
     * @param soidMsg soid of the object for which GetComponentCall was made.
     *        It may not necessarily be same as soidRemote especially while
     *        processing alias msg. It's used for detecting cyclic dependency.
     * @param requested a set of OIDs for which this peer has previously requested information
     *        due to a name-conflict. See Download._requested for more information.
     * @return whether oids were merged on name conflict.
     */
    private boolean resolveNameConflict_(DID did, SOID soidRemote, OID parent, PBMeta meta,
            boolean wasPresent, int metaDiff, Trans t, SOID soidNoNewVersion, Version vRemote,
            final SOID soidMsg, Set<OCID> requested, CausalityResult cr)
            throws Exception
    {
        Path pParent = _ds.resolve_(new SOID(soidRemote.sidx(), parent));

        Path pLocal = pParent.append(meta.getName());
        SOID soidLocal = _ds.resolveNullable_(pLocal);
        assert soidLocal != null && soidLocal.sidx().equals(soidRemote.sidx()) :
                soidLocal + " " + soidRemote + " " + pLocal;
        OA oaLocal = _ds.getOA_(soidLocal);
        OA.Type typeRemote = fromPB(meta.getType());

        if (l.isDebugEnabled()) l.debug("name conflict on " + pLocal + ": local " + soidLocal.oid() +
                " " + oaLocal.type() + " remote " + soidRemote.oid() + " " + typeRemote);

        if (_sc.detectFolderToAnchorConversion_(soidLocal.oid(), oaLocal.type(), soidRemote.oid(),
                typeRemote)) {
            // The local folder has been converted to an anchor of the same name.
            // We don't want to generate a "depends-on" from the remote anchor
            // to the local folder to avoid cyclic dependency: if the local
            // folder has files to be migrated to the converted store, the
            // migration of the local folder would depend on the download of the
            // remote anchor. (See Migration.downloadEmigrantAncestorStores_()).
            //
            // As a workaround, the code below renames the local folder without
            // updating its version. Hopefully soon after the anchor is
            // downloaded the remote version of this folder will overwrite the
            // local renaming. If not, the folder name will become permanently
            // inconsistent with other peers.
            //
            l.debug("folder->anchor conversion detected: " + soidLocal + "->" + soidRemote);
            String newName = L.get().product() + " temporary folder - do not remove";

            while (_ds.resolveNullable_(pParent.append(newName)) != null) {
                FileName fn = FileName.fromBaseName(newName);
                newName = Util.nextName(fn.base, fn.extension);
            }

            _om.moveInSameStore_(soidLocal, parent, newName, PhysicalOp.APPLY, false, false, t);
            applyMeta_(did, soidRemote, meta, parent, wasPresent, metaDiff, t, soidNoNewVersion,
                    vRemote, soidMsg, requested, cr);
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
            if (!requested.contains(new OCID(soidLocal.oid(), CID.META))) {
                throw new ExNameConflictDependsOn(soidLocal.oid(), did, true, parent, vRemote,
                        meta, soidMsg, requested);
            }
        }

        // Either cyclic dependency or local object already sync'ed
        l.debug("true name conflict");

        // Resolve this name conflict by aliasing only if
        // 1) the remote object is not present locally,
        // 2) the remote object is not an anchor,
        // and 3) if the local and remote types of the object are equivalent
        if (!wasPresent && meta.getType() != PBMeta.Type.ANCHOR && typeRemote == oaLocal.type()) {
            // Resolving name conflict by aliasing the oids.
            _al.resolveNameConflictOnNewRemoteObjectByAliasing_(soidRemote, soidLocal, parent,
                vRemote, meta, soidNoNewVersion, t);
            return true;
        } else {
            resolveNameConflictByRenaming_(did, soidRemote, soidLocal, wasPresent, parent, pParent,
                    vRemote, meta, metaDiff, soidMsg, requested, cr, t);
            return false;
        }
    }

    public void resolveNameConflictByRenaming_(DID did, SOID soidRemote, SOID soidLocal,
            boolean wasPresent, OID parent, Path pParent, Version vRemote, PBMeta meta,
            int metaDiff, SOID soidMsg, final Set<OCID> requested, CausalityResult cr, Trans t)
            throws Exception
    {
        // Resolve name conflict by generating a new name.
        l.debug("Resolving name conflicts by renaming one of the oid.");

        int comp = soidLocal.compareTo(soidRemote);
        assert comp != 0;
        String newName = _ds.generateNameConflictFileName_(pParent, meta.getName());
        if (comp > 0) {
            // local wins
            l.debug("change remote name");
            PBMeta newMeta = PBMeta.newBuilder().mergeFrom(meta).setName(newName).build();
            applyMeta_(did, soidRemote, newMeta, parent, wasPresent, metaDiff, t, null,
                    vRemote, soidMsg, requested, cr);

            // The version for soidRemote should be incremented, (with VersionUpdater.update_)
            // Unfortunately that can't be done here, as applyUpdateMetaAndContent will detect
            // that the version changed during the application of the update, and throw ExAborted,
            // effectively making this code path a no-op. Instead, set cr._conflictRename to true
            // so that applyUpdateMetaAndContent will increment the version for us.
            cr._conflictRename = true;
        } else {
            // remote wins
            l.debug("change local name");
            _om.moveInSameStore_(soidLocal, parent, newName, PhysicalOp.APPLY, false, true, t);
            applyMeta_(did, soidRemote, meta, parent, wasPresent, metaDiff, t, null,
                    vRemote, soidMsg, requested, cr);
        }
    }

    private int copyOneChunk(ByteArrayInputStream from, OutputStream to)
        throws IOException
    {
        // N.B. we can't use Util.copy because it uses the file buffer size, not the max
        // unicast packet size.
        byte[] buf = new byte[_m.getMaxUnicastSize_()];
        int total = 0;
        while (true) {
            int read = from.read(buf);
            if (read == -1) break;
            to.write(buf, 0, read);
            total += read;
        }
        return total;
    }

    public Trans applyContent_(DigestedMessage msg, SOCKID k, KIndex kidxOld,
            boolean wasPresent, Version vRemote, CausalityResult res, Token tk)
            throws SQLException, IOException, ExDependsOn, ExTimeout, ExAborted, ExStreamInvalid,
            ExNoResource, ExOutOfSpace, ExNotFound, DigestException
    {
        PBGetComReply reply = msg.pb().getGetComReply();

        // check for over quota and abort the stream as early as possible so
        // we don't waste transfer if the store is over quota
        long estTotalLen = reply.getFileTotalLength();
        if (_sidx2s.getThrows_(msg.sidx()).isOverQuota_(estTotalLen)) {
            l.warn("dl might over quota w/ est. len " + estTotalLen);
            throw new ExOutOfSpace();
        }

        // Should aliased oid be checked?
        // Since there is no content associated with aliased oid
        // there shouldn't be invocation for applyContent_()?

        final IPhysicalPrefix pfPrefix = _ps.newPrefix_(k);

        // TODO reserve space first

        Version vPrefixOld = _pvc.getPrefixVersion_(k.soid(), k.kidx());

        final boolean append;
        if (reply.getPrefixLength() == 0) {
            // write the prefix version before we change the tmp file
            if (msg.streamKey() != null || !vPrefixOld.isZero_()) {
                Trans t = _tm.begin_();
                try {
                    if (!vPrefixOld.isZero_()) {
                        _pvc.deletePrefixVersion_(k.soid(), k.kidx(), t);
                    }

                    // don't bother enabling inc dl for atomic messages
                    if (msg.streamKey() != null) {
                        _pvc.addPrefixVersion_(k.soid(), k.kidx(), vRemote, t);
                    }

                    t.commit_();

                } finally {
                    t.end_();
                }
            }

            // this truncates the file to size zero
            append = false;

        } else if (kidxOld.equals(k.kidx())) {
            // if vPreOld != vRemote, the prefix version must be updated in the
            // persistent store (see above).
            assert vPrefixOld.equals(vRemote);

            l.debug("prefix accepted: " + reply.getPrefixLength());
            append = true;

        } else {
            // kidx of the prefix file has been changed. this may happen if
            // after the last partial download and before the current download,
            // the local peer modified the branch being downloaded, causing a
            // new conflict. we in this case reuse the prefix file for the new
            // branch. the following assertion is because local peers should
            // only be able to change the MASTER branch.
            assert kidxOld.equals(KIndex.MASTER);

            Trans t = _tm.begin_();
            try {
                // TODO (DF) : figure out if prefix files need a KIndex or are assumed
                // to be MASTER like everything else in Download
                IPhysicalPrefix pfPrefixOld = _ps.newPrefix_(new SOCKID(k.soid(),
                    k.cid(), kidxOld));
                assert pfPrefixOld.getLength_() > 0;

                pfPrefixOld.moveTo_(pfPrefix, t);

                // note: transaction may fail (i.e. process_ crashes) after the
                // above move and before the commit below, which is fine.

                _pvc.deletePrefixVersion_(k.soid(), kidxOld, t);
                _pvc.addPrefixVersion_(k.soid(), k.kidx(), vRemote, t);

                t.commit_();
            } finally {
                t.end_();
            }

            l.debug("prefix transferred " + kidxOld + "->" + k.kidx() + ": " +
                reply.getPrefixLength());
            append = true;
        }

        // code after the creation of 'os' must go into the try block below so
        // that it will be properly closed

        final OutputStream os = pfPrefix.newOutputStream_(append);
        try {
            // assert after opening the stream otherwise the file length may
            // have changed after the assertion and before newOutputStream()
            assert pfPrefix.getLength_() == reply.getPrefixLength() :
                k + " " + pfPrefix.getLength_() + " != " + reply.getPrefixLength();

            ByteArrayInputStream is = msg.is();
            int copied = copyOneChunk(is, os);

            if (msg.streamKey() != null) {
                // it's a stream
                long total = reply.getFileTotalLength();
                long remaining = total - copied - reply.getPrefixLength();
                while (remaining > 0) {
                    _dlState.ongoing_(k.socid(), msg.ep(), total - remaining, total);
                    is = _iss.recvChunk_(msg.streamKey(), tk);
                    remaining -= copyOneChunk(is, os);
                }
                assert remaining >= 0;
            }

        } finally {
            os.close();
        }

        ContentHash h = pfPrefix.prepare_(tk);

        // get length and mtime of the prefix before the actual transaction.
        long len = pfPrefix.getLength_();

        // Use the hash supplied by the sender, if available. Hash of content should be available
        // for non-master branches and hence if not sent by sender then compute it.
        if (h == null) {
            if (res._hash != null) {
                // res._hash may be null if the remote version vector is dominating or sender didn't
                // send hash.
                h = res._hash;
            } else if (!k.kidx().equals(KIndex.MASTER)) {
                // Computing hash may pause hence it can't be done in middle of transaction.
                h = _hasher.computeHash_(pfPrefix, tk);
            }
        }

        boolean okay = false;
        Trans t = _tm.begin_();
        try {
            // can't use the old values as the attributes might have changed
            // during pauses, due to aliasing and such
            OA oa = _ds.getOAThrows_(k.soid());
            Path path = _ds.resolve_(oa);
            IPhysicalFile pf = _ps.newFile_(k.sokid(), path);

            // abort if the object is expelled. Although Download.java checks
            // for this condition before starting the download, but the object
            // may be expelled during pauses of the current thread.
            if (oa.isExpelled()) throw new ExAborted(k + " becomes offline");

            CA ca = oa.caNullable(k.kidx());
            wasPresent = ca != null;
            if (wasPresent && pf.wasModifiedSince(ca.mtime(), ca.length())) {
                // the linked file modified via the local filesystem
                // (i.e. the linker), but the linker hasn't received
                // the notification yet. we should not overwrite the
                // file in this case otherwise the local update will get
                // lost.
                //
                // BUGBUG NB there is still a tiny time window between the
                // test above and the apply_() below that the file is
                // updated via the filesystem.
                throw new ExAborted(k + " has changed locally");
            }

            assert reply.hasMtime();
            long replyMTime = reply.getMtime();
            assert replyMTime >= 0 : Joiner.on(' ').join(replyMTime, k, vRemote, wasPresent);
            long mtime = _ps.apply_(pfPrefix, pf, wasPresent, replyMTime, t);

            assert msg.streamKey() == null ||
                _pvc.getPrefixVersion_(k.soid(), k.kidx()).equals(vRemote);

            _pvc.deletePrefixVersion_(k.soid(), k.kidx(), t);

            if (!wasPresent) {
                if (!k.kidx().equals(KIndex.MASTER)) {
                    // record creation of conflict branch here instead of in DirectoryService
                    // Aliasing and Migration may recreate them and we only want to record each
                    // conflict once
                    _a.incConflictCount();
                }
                _ds.createCA_(k.soid(), k.kidx(), t);
            }
            _ds.setCA_(k.sokid(), len, mtime, h, t);

            okay = true;
            return t;

        } finally {
            if (!okay) t.end_();
        }
    }

    /**
     * Delete obsolete branches, update version vectors
     */
    public void applyUpdateMetaAndContent_(SOCKID k, Version vRemote, CausalityResult res, Trans t)
            throws SQLException, IOException, ExNotFound, ExAborted
    {
        // delete branches
        for (KIndex kidxDel : res._kidcsDel) {
            // guaranteed by computeCausalityForContent()'s logic
            assert !kidxDel.equals(KIndex.MASTER);

            SOCKID kDel = new SOCKID(k.socid(), kidxDel);
            Version vDel = _nvc.getLocalVersion_(kDel);

            // guaranteed by computeCausalityForContent()'s logic
            assert !vRemote.sub_(vDel).isZero_();

            _bd.deleteBranch_(kDel, vDel, true, t);
        }

        Version vKML = _nvc.getKMLVersion_(k.socid());
        Version vKML_R = vKML.sub_(vRemote);
        Version vDelKML = vKML.sub_(vKML_R);

        if (l.isDebugEnabled()) {
            l.debug(k + ": r " + vRemote + " kml " + vKML + " -kml " + vDelKML +
                " +l " + res._vAddLocal + " -kidx " + res._kidcsDel);
        }

        // check if the local version has changed during our pauses
        //
        // N.B. (MJ) it appears that if vKLocal is empty, it is possible that res._vLocal can
        // be null. The following could be replaced with a simpler
        //  if (!_nvc.getLocalVersion_(k).sub_(res._vLocal).isZero_())
        //     throw new ExAborted(k + " version changed locally.");
        // but because res._vLocal can be null, I want to assert that it is not (when it is safe to
        // do so).
        Version vKLocal = _nvc.getLocalVersion_(k);
        if (vKLocal.isZero_()) {
            // no-op -- the local version is zero, so hasn't changed during the pauses.
            // N.B. (markj) I think res._vLocal is permitted to be null here. I *think* it is
            // because the local version at the beginning of processing was zero, though I can't
            // guarantee that.
        } else {
            // the local version is non-zero, so subtract the version recorded in the causality
            // result from before the processing of this update started.
            assert !vKLocal.isZero_();
            assert res._vLocal != null : k + " " + vRemote + " " + vKLocal + " " + res;
            if (!vKLocal.sub_(res._vLocal).isZero_()) {
                throw new ExAborted(k + " version changed locally.");
            }
        }

        // update version vectors
        _nvc.deleteKMLVersion_(k.socid(), vDelKML, t);
        _nvc.addLocalVersion_(k, res._vAddLocal, t);

        // increment the version if
        // 1) this update was a merge of true conflicts OR
        // 2) the object in the msg was renamed to resolve a local name conflict
        if (res._incrementVersion || res._conflictRename) {
            assert k.cid().isMeta();
            _vu.update_(k, t);
        }
    }
}
