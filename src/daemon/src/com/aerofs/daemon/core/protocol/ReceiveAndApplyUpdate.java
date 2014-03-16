/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.base.analytics.Analytics;
import com.aerofs.base.analytics.AnalyticsEvents.FileConflictEvent;
import com.aerofs.base.analytics.IAnalyticsEvent;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.Hasher;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.alias.Aliasing;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.ex.ExOutOfSpace;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.IncomingStreams;
import com.aerofs.daemon.core.net.IncomingStreams.StreamKey;
import com.aerofs.daemon.core.object.BranchDeleter;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.transfers.download.DownloadState;
import com.aerofs.daemon.core.transfers.download.ExUnsolvedMetaMetaConflict;
import com.aerofs.daemon.core.transfers.download.IDownloadContext;
import com.aerofs.daemon.core.transfers.download.dependence.DependencyEdge.DependencyType;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.lib.exception.ExDependsOn;
import com.aerofs.daemon.lib.exception.ExNameConflictDependsOn;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.labeling.L;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Path;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.analytics.AnalyticsEventCounter;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.OCID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.proto.Core.PBGetComReply;
import com.aerofs.proto.Core.PBMeta;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;

import static com.aerofs.daemon.core.protocol.GetComponentReply.fromPB;

public class ReceiveAndApplyUpdate
{
    private static final Logger l = Loggers.getLogger(ReceiveAndApplyUpdate.class);

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
    private Aliasing _al;
    private MapAlias2Target _a2t;
    private BranchDeleter _bd;
    private TransManager _tm;
    private AnalyticsEventCounter _conflictCounter;

    @Inject
    public void inject_(DirectoryService ds, PrefixVersionControl pvc, NativeVersionControl nvc,
            Hasher hasher, VersionUpdater vu, ObjectCreator oc, ObjectMover om,
            IPhysicalStorage ps, DownloadState dlState, ComputeHashCall computeHashCall, StoreCreator sc,
            IncomingStreams iss, Aliasing al, BranchDeleter bd, TransManager tm,
            MapAlias2Target alias2target, Analytics analytics)
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
        _al = al;
        _bd = bd;
        _tm = tm;
        _a2t = alias2target;
        _conflictCounter = new AnalyticsEventCounter("analytics-file-conflict", analytics)
        {
            @Override
            public IAnalyticsEvent createEvent(int count)
            {
                return new FileConflictEvent(count);
            }
        };
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
        // if content I/O should be avoided
        public final boolean _avoidContentIO;

        @Nullable public final ContentHash _hash;

        public final Version _vLocal;

        CausalityResult(KIndex kidx, Version vAddLocal, Version vLocal)
        {
            this(kidx, vAddLocal, vLocal, false);
        }

        CausalityResult(KIndex kidx, Version vAddLocal, Version vLocal, boolean avoidContentIO)
        {
            this(kidx, vAddLocal, Collections.<KIndex>emptyList(), false, null, vLocal,
                    avoidContentIO);
        }

        CausalityResult(@Nonnull KIndex kidx, @Nonnull Version vAddLocal,
                @Nonnull Collection<KIndex> kidcsDel, boolean incrementVersion,
                @Nullable ContentHash h, @Nonnull Version vLocal)
        {
            this(kidx, vAddLocal, kidcsDel, incrementVersion, h, vLocal, false);
        }


        CausalityResult(@Nonnull KIndex kidx, @Nonnull Version vAddLocal,
            @Nonnull Collection<KIndex> kidcsDel, boolean incrementVersion,
            @Nullable ContentHash h, @Nonnull Version vLocal, boolean avoidContentIO)
        {
            _kidx = kidx;
            _vAddLocal = vAddLocal;
            _incrementVersion = incrementVersion;
            _kidcsDel = kidcsDel;
            _hash = h;
            _vLocal = vLocal;
            _conflictRename = false;
            _avoidContentIO = avoidContentIO;
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
        int metaDiff) throws SQLException, ExUnsolvedMetaMetaConflict
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
                // TODO: throw to prevent meta/meta conflicts from being ignored when aliasing?
                l.warn("true meta conflict on {}. {} > {}. don't apply", soid, vLocal, vRemote);
                throw new ExUnsolvedMetaMetaConflict();
            } else {
                l.debug("true meta conflict. l < r. merge");
                return new CausalityResult(KIndex.MASTER, vR_L, Collections.<KIndex>emptyList(),
                        true, null, vLocal);
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
        l.debug("Local hash: {} Remote hash: {}", hLocal, hRemote);
        boolean result = hRemote.equals(hLocal);
        l.debug("Comparing hashes: {} {}", result, k);
        return result;
    }

    /**
     * Reads the content hash of a remote file from an incoming datagram's InputStream.
     *
     * @param is The InputStream from which to read the hash
     * @param hashLength The length of the hash
     * @return The ContentHash read from the message
     */
    private ContentHash readContentHashFromDatagram(InputStream is, int hashLength)
            throws IOException
    {
        // If the protobuf, hash, and file content all fit into a single datagram,
        // then the hash and file content will be found in message.is().
        DataInputStream dis = new DataInputStream(is);
        byte[] hashBuf = new byte[hashLength];
        dis.readFully(hashBuf);
        // N.B. don't close is - it'll close msg.is(), which will keep us from reading
        // the file content after the hash.
        return new ContentHash(hashBuf);
    }

    /**
     * Reads the content hash of a remote file from an incoming stream. This method will
     * release the CoreLock and block to wait for incoming stream chunks.
     *
     * @param streamKey The stream from which to read the ContentHash
     * @param is The stream of bytes we have already received from the stream
     * @param hashLength The length of the hash
     * @param tk The token to use when releasing the CoreLock
     * @return The ContentHash read from the incoming stream
     */
    private ContentHash readContentHashFromStream(StreamKey streamKey, InputStream is,
            int hashLength, Token tk)
            throws IOException, ExAborted, ExTimeout, ExStreamInvalid, ExNoResource
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        // First copy any data that we have already received from the stream
        long hashBytesRead = ByteStreams.copy(is, os);

        while (hashBytesRead < hashLength) {
            // This code assumes that the hash and following data will arrive in separate
            // chunks.  While this is true now, it may not be once the underlying layers
            // are allowed to fragment/reassemble.
            is = _iss.recvChunk_(streamKey, tk);
            try {
                hashBytesRead += ByteStreams.copy(is, os);
            } finally {
                is.close();
            }
            l.debug("Read " + hashBytesRead + " hash bytes of " + hashLength);
        }
        return new ContentHash(os.toByteArray());
    }

    /**
     * @return null if not to apply the update
     */
    public @Nullable CausalityResult computeCausalityForContent_(SOID soid,
            Version vRemote, DigestedMessage msg, Token tk)
            throws Exception
    {
        final PBGetComReply reply = msg.pb().getGetComReply();
        final @Nullable ContentHash hRemote;
        if (reply.hasIsContentSame() && reply.getIsContentSame()) {
            // requested remote version has same content as the MASTER version we had when we made
            // the request -> add version to MASTER without any file I/O
            Version vMaster = _nvc.getLocalVersion_(new SOCKID(soid, CID.CONTENT, KIndex.MASTER));
            return new CausalityResult(KIndex.MASTER, vRemote.sub_(vMaster), vMaster, true);
        } else if (reply.hasHashLength()) {
            l.debug("hash included");

            if (msg.streamKey() != null) {
                hRemote = readContentHashFromStream(msg.streamKey(), msg.is(),
                        reply.getHashLength(), tk);
            } else {
                hRemote = readContentHashFromDatagram(msg.is(), reply.getHashLength());
            }
        } else {
            l.debug("hash not present");
            hRemote = null;
        }

        Version vAddLocal = Version.copyOf(vRemote);
        KIndex kidxApply = null;
        @Nullable Version vApply = null;
        List<KIndex> kidcsDel = Lists.newArrayList();

        OA remoteOA = _ds.getOAThrows_(soid);

        // MASTER branch should be considered first for application of update as opposed
        // to conflict branches when possible (see "@@" below).
        // Hence iterate over ordered KIndices.
        for (KIndex kidx : remoteOA.cas().keySet()) {
            SOCKID kBranch = new SOCKID(soid, CID.CONTENT, kidx);
            Version vBranch = _nvc.getLocalVersion_(kBranch);

            if (l.isDebugEnabled()) l.debug(kBranch + " l " + vBranch);

            if (vRemote.sub_(vBranch).isZero_()) {
                // The local version is newer or the same as the remote version

                // This SOCKID (kBranch) is a local conflict branch of the remotely-received SOID.
                // If it was expelled it would not have been a member of the CA set. Therefore it
                // should always be present.
                assert _ds.isPresent_(kBranch) : kBranch;

                l.warn("l - r > 0");

                // No work to be done
                return null;
            }

            // the local version is older or in parallel

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

            //noinspection StatementWithEmptyBody
            if (isRemoteDominating || isContentSame_(kBranch, hRemote, tk)) {
                l.debug("content is the same! " + isRemoteDominating + " " + hRemote);
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

            if (l.isDebugEnabled()) {
                l.debug("kidx: " + kidx.getInt() + " vAddLocal: " + vAddLocal);
            }
        }

        if (kidxApply == null)  {
            // No subordinate branch was found. Create a new branch.
            SortedMap<KIndex, CA> cas = remoteOA.cas();
            kidxApply = cas.isEmpty() ? KIndex.MASTER : cas.lastKey().increment();

            // The local version should be empty since we have no local branch
            vApply = _nvc.getLocalVersion_(new SOCKID(soid, CID.CONTENT, kidxApply));
            assert vApply.isZero_() : vApply + " " + soid;
        }

        // To change a version, we must add the diff of what is to be applied, from the version
        // currently stored locally. Thus the value of vAddLocal up to this point represented
        // the entire version to be applied locally. Now we reset it to be the diff for the sake
        // of version arithmetic.
        vAddLocal = vAddLocal.sub_(vApply);

        if (l.isDebugEnabled()) {
            l.debug("Final vAddLocal: " + vAddLocal + " kApply: " + kidxApply);
        }
        return new CausalityResult(kidxApply, vAddLocal, kidcsDel, false, hRemote, vApply);
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
                        meta.getFlags(), PhysicalOp.APPLY, true, false, t);

            } else {
                if (Util.test(metaDiff, MetaDiff.PARENT | MetaDiff.NAME)) {

                    resolveParentConflictIfRemoteParentIsLocallyNestedUnderChild_(soid.sidx(),
                            soid.oid(), oidParent, t);

                    _om.moveInSameStore_(soid, oidParent, meta.getName(), PhysicalOp.APPLY, false,
                            false, t);
                }
            }
        } catch (ExAlreadyExist e) {
            l.warn("name conflict {} in {}", soid, cxt);
            return resolveNameConflict_(soid, oidParent, meta, wasPresent, metaDiff, t,
                    noNewVersion, vRemote, soidMsg, cr, cxt);
        } catch (ExNotDir e) {
            SystemUtil.fatal(e);
        }

        return false;
    }

    private void resolveParentConflictIfRemoteParentIsLocallyNestedUnderChild_(SIndex sidx,
            OID child, OID remoteParent, Trans t)
            throws SQLException, IOException
    {
        SOID soidRemoteParent = new SOID(sidx, remoteParent);
        OA oaChild = _ds.getOA_(new SOID(sidx, child));

        Path pathRemoteParent = _ds.resolve_(soidRemoteParent);
        Path pathChild = _ds.resolve_(oaChild);

        if (pathRemoteParent.isUnder(pathChild)) {
            // A cyclic dependency would result if we tried to apply this update.
            // The current approach to resolve this conflict is to move the to-be parent object
            // under the child's current parent. It's not beautiful but works and will reach
            // consistency  across devices.
            l.debug("resolve remote parent is locally nested under child " + child + " "
                    + remoteParent);

            try {
                // Avoid a local name conflict in the new path of the remote parent object
                String newRemoteParentName = _ds.generateConflictFreeFileName_(
                        pathChild.removeLast(), pathRemoteParent.last());

                _om.moveInSameStore_(soidRemoteParent, oaChild.parent(), newRemoteParentName,
                        PhysicalOp.APPLY, false, true, t);

            } catch (ExNotFound e) {
                SystemUtil.fatal(e);
            } catch (ExNotDir e) {
                SystemUtil.fatal(e);
            } catch (ExAlreadyExist e) {
                SystemUtil.fatal(e);
            } catch (ExStreamInvalid e) {
                SystemUtil.fatal(e);
            }
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
     * @return whether oids were merged on name conflict.
     */
    private boolean resolveNameConflict_(SOID soidRemote, OID parent, PBMeta meta,
            boolean wasPresent, int metaDiff, Trans t, SOID soidNoNewVersion, Version vRemote,
            final SOID soidMsg, CausalityResult cr, IDownloadContext cxt)
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
            String newName = L.product() + " temporary folder - do not remove";

            while (_ds.resolveNullable_(pParent.append(newName)) != null) {
                newName = Util.nextFileName(newName);
            }

            _om.moveInSameStore_(soidLocal, parent, newName, PhysicalOp.APPLY, false, false, t);
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
            _al.resolveNameConflictOnNewRemoteObjectByAliasing_(soidRemote, soidLocal, parent,
                vRemote, meta, soidNoNewVersion, t);
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
            _om.moveInSameStore_(soidLocal, parent, newName, PhysicalOp.APPLY, false, true, t);
            applyMeta_(soidRemote, meta, parent, wasPresent, metaDiff, t, null,
                    vRemote, soidMsg, cr, cxt);
        }
    }

    private void updatePrefixVersion_(SOCKID k, Version vRemote, boolean isStreaming)
            throws SQLException
    {
        Version vPrefixOld = _pvc.getPrefixVersion_(k.soid(), k.kidx());

        if (isStreaming || !vPrefixOld.isZero_()) {
            Trans t = _tm.begin_();
            try {
                if (!vPrefixOld.isZero_()) {
                    _pvc.deletePrefixVersion_(k.soid(), k.kidx(), t);
                }

                // If we're not streaming data, then the download can't be interrupted.
                // Therefore there is no point to maintain version info for the prefix.
                if (isStreaming) {
                    _pvc.addPrefixVersion_(k.soid(), k.kidx(), vRemote, t);
                }

                t.commit_();

            } finally {
                t.end_();
            }
        }
    }

    private void movePrefixFile_(SOCID socid, KIndex kFrom, KIndex kTo, Version vRemote)
            throws SQLException, IOException
    {
        Trans t = _tm.begin_();
        try {
            // TODO (DF) : figure out if prefix files need a KIndex or are assumed
            // to be MASTER like everything else in Download
            IPhysicalPrefix from = _ps.newPrefix_(new SOCKID(socid, kFrom), null);
            assert from.getLength_() > 0;

            IPhysicalPrefix to = _ps.newPrefix_(new SOCKID(socid, kTo), null);
            from.moveTo_(to, t);

            // note: transaction may fail (i.e. process_ crashes) after the
            // above move and before the commit below, which is fine.

            _pvc.deletePrefixVersion_(socid.soid(), kFrom, t);
            _pvc.addPrefixVersion_(socid.soid(), kTo, vRemote, t);

            t.commit_();
        } finally {
            t.end_();
        }
    }

    /**
     * Opens the prefix file for writing. If there is no prefix length or if the content we are
     * downloading is present locally, then we overwrite the prefix file and update the version
     * of the prefix. Otherwise writing to the returned OutputStream will append to it.
     *
     * NOTE: This method screams for the need to be split up. Look at the name... just look at the
     * name and tell me everything's going to be okay! That's right, lie through your teeth!
     *
     * @param prefix The prefix file to open for writing
     * @param k The SOCKID of the prefix file
     * @param vRemote The version vector of the remote object
     * @param prefixLength The amount of data we already have in the prefix file
     * @param contentPresentLocally Whether the content for the remote object is present locally
     * @param isStreaming Whether we are streaming the content or receiving it in a datagram.
     * @return an OutputStream that writes to the prefix file
     */
    private OutputStream createPrefixOutputStreamAndUpdatePrefixState_(IPhysicalPrefix prefix,
            SOCKID k, Version vRemote, long prefixLength,
            boolean contentPresentLocally, boolean isStreaming)
            throws ExNotFound, SQLException, IOException
    {
        if (prefixLength == 0 || contentPresentLocally) {
            l.debug("update prefix version");

            // Write the prefix version before we write to the prefix file
            updatePrefixVersion_(k, vRemote, isStreaming);

            // This truncates the file to size zero
            return prefix.newOutputStream_(false);

        } else if (KIndex.MASTER.equals(k.kidx())) {
            // if vPrefixOld != vRemote, the prefix version must be updated in the
            // persistent store (see above).
            Version vPrefixOld = _pvc.getPrefixVersion_(k.soid(), k.kidx());

            assert vPrefixOld.equals(vRemote) : Joiner.on(' ').join(vPrefixOld, vRemote, k);

            l.debug("prefix accepted: " + prefixLength);
            return prefix.newOutputStream_(true);

        } else {
            // kidx of the prefix file has been changed. this may happen if
            // after the last partial download and before the current download,
            // the local peer modified the branch being downloaded, causing a
            // new conflict. we in this case reuse the prefix file for the new
            // branch. the following assertion is because local peers should
            // only be able to change the MASTER branch.
            movePrefixFile_(k.socid(), KIndex.MASTER, k.kidx(), vRemote);

            l.debug("prefix transferred " + KIndex.MASTER + "->" + k.kidx() + ": " + prefixLength);
            return prefix.newOutputStream_(true);
        }
    }

    /**
     * If the remote peer resolved a conflict and we're getting that update, chances are
     * one of our branches has the same content. Find that branch.
     *
     * @param object The object whose branches to search
     * @param remoteHash The hash of the remote object's content
     * @return The branch with the same content as the remote, or null
     */
    private @Nullable KIndex findBranchWithMatchingContent_(SOID object,
            @Nonnull ContentHash remoteHash)
            throws ExNotFound, SQLException
    {
        // See if we have the same content in one of our branches
        for (KIndex branch : _ds.getOAThrows_(object).cas().keySet()) {
            SOKID branchObject = new SOKID(object, branch);
            ContentHash localHash = _ds.getCAHash_(branchObject);
            if (localHash != null && localHash.equals(remoteHash)) {
                return branch;
            }
        }
        return null;
    }

    private void writeContentToPrefixFile_(IPhysicalPrefix pfPrefix, DigestedMessage msg,
            final long totalFileLength, final long prefixLength, SOCKID k,
            Version vRemote, @Nullable KIndex localBranchWithMatchingContent, Token tk)
            throws ExOutOfSpace, ExNotFound, ExStreamInvalid, ExAborted, ExNoResource, ExTimeout,
            SQLException, IOException, DigestException
    {
        final boolean isStreaming = msg.streamKey() != null;

        // Open the prefix file for writing, updating it as required
        final OutputStream os = createPrefixOutputStreamAndUpdatePrefixState_(pfPrefix, k,
                vRemote, prefixLength, localBranchWithMatchingContent != null, isStreaming);

        try {
            if (localBranchWithMatchingContent != null && isStreaming) {
                // We have this file locally and we are receiving the remote content via a stream.
                // We can cancel the stream here and use the local content.
                l.debug("reading content from local branch");

                // Stop the stream since we will not be reading it from now on
                _iss.end_(msg.streamKey());

                // Use the content from the local branch
                IPhysicalFile file = _ps.newFile_(_ds.resolve_(k.soid()),
                        localBranchWithMatchingContent);

                try {
                    InputStream is = file.newInputStream_();
                    try {
                        // release core lock to avoid blocking while copying a large prefix
                        TCB tcb = tk.pseudoPause_("cp-prefix");
                        try {
                            Util.copy(is, os);
                        } finally {
                            tcb.pseudoResumed_();
                        }
                    } finally {
                        is.close();
                    }
                } catch (FileNotFoundException e) {
                    SOCKID conflict = new SOCKID(k.socid(), localBranchWithMatchingContent);

                    if (!localBranchWithMatchingContent.equals(KIndex.MASTER)) {
                        // A conflict branch does not exist, even though there is an entry
                        // in our database. This is an inconsistency, we must remove the
                        // entry in our database. AeroFS will redownload the conflict at a later
                        // point.
                        l.error("known conflict branch has no associated file: " + conflict);

                        Trans t = _tm.begin_();
                        try {
                            _bd.deleteBranch_(conflict, _nvc.getLocalVersion_(conflict), false, t);
                            t.commit_();
                        } finally {
                            t.end_();
                        }
                    }

                    l.warn("can't copy content from local branch " + conflict);

                    throw new ExAborted(e.getMessage());
                }

            } else {
                // We do not have the content locally, or we do but we are receiving the update via
                // a datagram. The datagram is fully received at this point so there is no need to
                // read from the file system the same content we already have in memory.
                l.debug("reading content from network");

                // assert after opening the stream otherwise the file length may
                // have changed after the assertion and before newOutputStream()
                assert pfPrefix.getLength_() == prefixLength :
                        k + " " + pfPrefix.getLength_() + " != " + prefixLength;

                ElapsedTimer timer = new ElapsedTimer();

                // Read from the incoming message/stream
                InputStream is = msg.is();
                long copied = ByteStreams.copy(is, os);

                if (isStreaming) {
                    // it's a stream
                    long remaining = totalFileLength - copied - prefixLength;
                    while (remaining > 0) {
                        // sending notifications is not cheap, hence the rate-limiting
                        if (timer.elapsed() > DaemonParam.NOTIFY_THRESHOLD) {
                            _dlState.progress_(k.socid(), msg.ep(), totalFileLength - remaining,
                                    totalFileLength);
                            timer.restart();
                        }
                        is = _iss.recvChunk_(msg.streamKey(), tk);
                        remaining -= ByteStreams.copy(is, os);
                    }
                    assert !(remaining < 0) : k + " " + msg.ep() + " " + remaining;
                }
            }
        } finally {
            // we want to be extra sure that the file is synced to the disk and no write operation
            // is languishing in a kernel buffer for two reasons:
            //   * if the transaction commits we have to serve this file to other peers and it
            //     would be terribly uncool to serve a corrupted/partially synced copy
            //   * once the contents are written we adjust the file's mtime and if we allow a
            //     race between write() and utimes() we will end up with a timestamp mismatch
            //     between db and filesystem that cause spurious updates later on, thereby
            //     wasting CPU and bandwidth and causing extreme confusion for the end user.
            os.flush();
            if (os instanceof FileOutputStream) {
                ((FileOutputStream)os).getChannel().force(true);
            }
            os.close();
        }
    }

    /**
     * Computes the content hash for the downloaded SOCKID. If the remote sent us the content hash
     * then use that, otherwise try computing the content hash for conflict branches. We do not
     * compute the hash for the master branch because it might change often (local edits).
     *
     * @param k The SOCKID of the downloaded file
     * @param prefix The physical temporary file which holds the SOCKID's content
     * @param remoteHash The content hash the remote sent us for this SOCKID
     * @param tk A token to use when releasing the CoreLock
     * @return The content hash of the downloaded file, or null if no hash will be calculated
     */
    private @Nullable ContentHash computeNewContentHash_(SOCKID k, IPhysicalPrefix prefix,
            @Nullable ContentHash remoteHash, Token tk)
            throws IOException, ExAborted, ExTimeout, DigestException
    {
        // Use the hash supplied by the sender, if available. Hash of content should be available
        // for non-master branches and hence if not sent by sender then compute it.
        @Nullable ContentHash h = prefix.prepare_(tk);
        if (h == null) {
            if (remoteHash != null) {
                // res._hash may be null if the remote version vector is dominating or sender didn't
                // send hash.
                return remoteHash;
            } else if (!k.kidx().equals(KIndex.MASTER)) {
                // Computing hash may pause hence it can't be done in middle of transaction.
                return _hasher.computeHash_(prefix, tk);
            }
        }
        return h;
    }

    public Trans applyContent_(DigestedMessage msg, SOCKID k, Version vRemote,
            CausalityResult res, Token tk)
            throws SQLException, IOException, ExDependsOn, ExTimeout, ExAborted, ExStreamInvalid,
            ExNoResource, ExOutOfSpace, ExNotFound, DigestException
    {
        PBGetComReply reply = msg.pb().getGetComReply();

        // Should aliased oid be checked?
        // Since there is no content associated with aliased oid
        // there shouldn't be invocation for applyContent_()?

        // TODO reserve space first

        KIndex localBranchWithMatchingContent = res._hash == null ? null :
                findBranchWithMatchingContent_(k.soid(), res._hash);

        if (res._avoidContentIO || (localBranchWithMatchingContent != null &&
                localBranchWithMatchingContent.equals(k.kidx()))) {
            l.debug("content already there, avoid I/O altogether");
            // no point doing any file I/O...

            // close the stream, we're not going to read from it
            if (msg.streamKey() != null) {
                _iss.end_(msg.streamKey());
            }

            // TODO:FIXME ugh that's retarded, this code needs some serious refactoring
            return _tm.begin_();
        }

        final IPhysicalPrefix pfPrefix = _ps.newPrefix_(k, null);

        // Write the new content to the prefix file
        writeContentToPrefixFile_(pfPrefix, msg, reply.getFileTotalLength(),
                reply.getPrefixLength(), k, vRemote, localBranchWithMatchingContent, tk);

        @Nullable ContentHash newContentHash = computeNewContentHash_(k, pfPrefix, res._hash, tk);

        // get length of the prefix before the actual transaction.
        long len = pfPrefix.getLength_();
        boolean okay = false;
        Trans t = _tm.begin_();
        try {
            // can't use the old values as the attributes might have changed
            // during pauses, due to aliasing and such
            OA oa = _ds.getOAThrows_(k.soid());
            ResolvedPath path = _ds.resolve_(oa);
            IPhysicalFile pf = _ps.newFile_(path, k.kidx());

            // abort if the object is expelled. Although Download.java checks
            // for this condition before starting the download, but the object
            // may be expelled during pauses of the current thread.
            if (oa.isExpelled()) throw new ExAborted(k + " becomes offline");

            CA ca = oa.caNullable(k.kidx());
            boolean wasPresent = ca != null;
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
                pf.onUnexpectedModification_(ca.mtime());
                throw new ExAborted(k + " has changed locally: expected=("
                        + ca.mtime() + "," + ca.length() + ") actual=("
                        + pf.getLastModificationOrCurrentTime_() + "," + pf.getLength_() + ")");
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
                    _conflictCounter.inc();
                }
                _ds.createCA_(k.soid(), k.kidx(), t);
            }
            _ds.setCA_(k.sokid(), len, mtime, newContentHash, t);

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
        if (!_nvc.getLocalVersion_(k).sub_(res._vLocal).isZero_()) {
            throw new ExAborted(k + " version changed locally.");
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
