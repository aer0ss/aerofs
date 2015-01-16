/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.daemon.core.Hasher;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.IncomingStreams;
import com.aerofs.daemon.core.net.IncomingStreams.StreamKey;
import com.aerofs.daemon.core.object.BranchDeleter;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.transfers.download.IDownloadContext;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.proto.Core.PBGetComponentResponse;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.SortedMap;

import static com.google.common.base.Preconditions.checkState;

/**
 * This class is responsible for handling GetComponentResponse messages for CONTENT components
 * and updating the local DB/fs to match the state on the remote peer.
 */
public class ContentUpdater
{
    private final static Logger l = Loggers.getLogger(ContentUpdater.class);

    private final TransManager _tm;
    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final IncomingStreams _iss;
    private final MapAlias2Target _a2t;
    private final LocalACL _lacl;
    private final NativeVersionControl _nvc;
    private final BranchDeleter _bd;
    private final Hasher _hasher;
    private final ComputeHash _computeHash;
    private final ReceiveAndApplyUpdate _raau;
    private final ChangeEpochDatabase _cedb;
    private final CentralVersionDatabase _cvdb;
    private final ContentChangesDatabase _ccdb;
    private final RemoteContentDatabase _rcdb;

    @Inject
    public ContentUpdater(TransManager tm, DirectoryService ds, IPhysicalStorage ps,
            IncomingStreams iss, MapAlias2Target a2t, LocalACL lacl, NativeVersionControl nvc,
            BranchDeleter bd, Hasher hasher, ComputeHash computeHash, ReceiveAndApplyUpdate raau,
            ChangeEpochDatabase cedb, CentralVersionDatabase cvdb, ContentChangesDatabase ccdb,
            RemoteContentDatabase rcdb)
    {
        _tm = tm;
        _ds = ds;
        _ps = ps;
        _iss = iss;
        _a2t = a2t;
        _lacl = lacl;
        _nvc = nvc;
        _bd = bd;
        _hasher = hasher;
        _computeHash = computeHash;
        _raau = raau;
        _cedb = cedb;
        _cvdb = cvdb;
        _ccdb = ccdb;
        _rcdb = rcdb;
    }

    void processContentResponse_(SOCID socid, DigestedMessage msg, IDownloadContext cxt)
            throws Exception
    {
        final PBGetComponentResponse pbResponse = msg.pb().getGetComponentResponse();

        // see Rule 2 in acl.md
        if (!_lacl.check_(msg.user(), socid.sidx(), Permissions.EDITOR)) {
            l.warn("{} on {} has no editor perm for {}", msg.user(), msg.ep(), socid.sidx());
            throw new ExSenderHasNoPerm();
        }

        // We should abort when receiving content for an aliased object
        if (_a2t.isAliased_(socid.soid())) {
            throw new ExAborted(socid + " aliased");
        }

        /////////////////////////////////////////
        // determine causal relation

        Version vRemote = Version.fromPB(pbResponse.getVersion());
        CausalityResult cr = computeCausality_(socid.soid(), vRemote, msg, cxt.token());

        if (cr == null) return;

        // This is the branch to which the update should be applied, as determined by
        // ReceiveAndApplyUpdate#computeCausalityForContent_
        SOKID targetBranch = new SOKID(socid.soid(), cr._kidx);

        /////////////////////////////////////////
        // apply update

        // N.B. vLocal.isZero_() doesn't mean the component is new to us.
        // It may be the case that it's not new but all the local ticks
        // have been replaced by remote ticks.

        // TODO merge/delete branches (variable kidcs), including their prefix files, that
        // are dominated by the new version

        IPhysicalPrefix prefix = null;
        ContentHash h;
        // TODO: allow copying from other files with same hash and from sync history
        KIndex localBranchWithMatchingContent = cr._hash == null ? null :
                findBranchWithMatchingContent_(socid.soid(), cr._hash);

        if (cr._avoidContentIO || (localBranchWithMatchingContent != null &&
                                            localBranchWithMatchingContent.equals(cr._kidx))) {
            l.debug("content already there, avoid I/O altogether");
            // no point doing any file I/O...

            // close the stream, we're not going to read from it
            if (msg.streamKey() != null) {
                _iss.end_(msg.streamKey());
            }

            h = cr._hash;
        } else {
            prefix = _ps.newPrefix_(targetBranch, null);
            h = _raau.download_(prefix, msg, targetBranch, vRemote, cr._hash,
                    localBranchWithMatchingContent, cxt.token());
        }

        Trans t = _tm.begin_();
        Throwable rollbackCause = null;
        try {
            updateVersion_(new SOCKID(targetBranch, CID.CONTENT), vRemote, cr, t);
            if (prefix != null) {
                _raau.apply_(targetBranch, msg.pb().getGetComponentResponse(), prefix, h, t);
            }
            t.commit_();
        } catch (Exception | Error e) {
            rollbackCause = e;
            throw e;
        } finally {
            t.end_(rollbackCause);
        }
        l.info("{} ok {}", msg.ep(), socid);
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

    public static class CausalityResult
    {
        // the kidx to which the downloaded update will be applied
        public final KIndex _kidx;
        // the version vector to be added to the branch corresponding to kidxApply
        public final Version _vAddLocal;
        // the branches to be deleted. never be null
        public final Collection<KIndex> _kidcsDel;
        // if content I/O should be avoided
        public final boolean _avoidContentIO;

        @Nullable public final ContentHash _hash;

        public final Version _vLocal;


        CausalityResult(@Nonnull KIndex kidx, @Nonnull Version vAddLocal,
                @Nonnull Collection<KIndex> kidcsDel,
                @Nullable ContentHash h, @Nonnull Version vLocal, boolean avoidContentIO)
        {
            _kidx = kidx;
            _vAddLocal = vAddLocal;
            _kidcsDel = kidcsDel;
            _hash = h;
            _vLocal = vLocal;
            _avoidContentIO = avoidContentIO;
        }

        @Override
        public String toString()
        {
            return Joiner.on(' ').useForNull("null")
                    .join(_kidx, _vAddLocal, _kidcsDel, _hash, _vLocal);
        }
    }

    /**
     * @return null if not to apply the update
     */
    public @Nullable CausalityResult computeCausality_(SOID soid, Version vRemote,
            DigestedMessage msg, Token tk)
            throws Exception
    {
        OA remoteOA = _ds.getOAThrows_(soid);
        if (remoteOA.isExpelled()) throw new ExAborted("expelled " + soid);

        List<KIndex> kidcsDel = Lists.newArrayList();

        final PBGetComponentResponse response = msg.pb().getGetComponentResponse();
        final @Nullable ContentHash hRemote;
        if (response.hasHashLength()) {
            l.debug("hash included");
            if (msg.streamKey() != null) {
                hRemote = readContentHashFromStream(msg.streamKey(), msg.is(),
                        response.getHashLength(), tk);
            } else {
                hRemote = readContentHashFromDatagram(msg.is(), response.getHashLength());
            }
        } else {
            hRemote = null;
        }

        Long rcv = vRemote.unwrapCentral();
        boolean usePolaris = _cedb.getChangeEpoch_(soid.sidx()) != null;

        if (rcv != null) {
            if (!usePolaris) throw new ExAborted("incompatible versioning");

            Long lcv = _cvdb.getVersion_(soid.sidx(), soid.oid());
            if (lcv != null && lcv >= rcv) {
                l.info("local {} >= {} remote", lcv, rcv);
                return null;
            }

            if (!response.hasHashLength()) throw new ExProtocolError();

            // TODO(phoenix): validate against RCDB entries
            KIndex target = KIndex.MASTER;
            boolean avoidContentIO = false;
            SortedMap<KIndex, CA> cas = remoteOA.cas();

            if (cas.size() > 1 || _ccdb.hasChange_(soid.sidx(), soid.oid())) {
                checkState(cas.size() <= 2);
                ContentHash h = _ds.getCAHash_(new SOKID(soid, KIndex.MASTER));

                // if the MASTER CA matches the remote object, avoid creating a conflict
                if (h != null && h.equals(hRemote)
                        && cas.get(KIndex.MASTER).length() == response.getFileTotalLength()) {
                    target = KIndex.MASTER;
                    avoidContentIO = true;
                    // merge if local change && remote hash == local hash
                    if (cas.size() > 1) {
                        kidcsDel.add(KIndex.MASTER.increment());
                    }
                } else {
                    target = KIndex.MASTER.increment();
                }
            }
            return new CausalityResult(target, vRemote, kidcsDel, hRemote,
                    Version.wrapCentral(lcv), avoidContentIO);
        } else if (usePolaris) {
            throw new ExAborted("incompatible versioning");
        }

        if (response.hasIsContentSame() && response.getIsContentSame()) {
            // requested remote version has same content as the MASTER version we had when we made
            // the request -> add version to MASTER without any file I/O
            Version vMaster = _nvc.getLocalVersion_(new SOCKID(soid, CID.CONTENT, KIndex.MASTER));

            // cleanup branches dominated by remote
            for (KIndex kidx : remoteOA.cas().keySet()) {
                if (kidx.isMaster()) continue;
                Version vBranch = _nvc.getLocalVersion_(new SOCKID(soid, CID.CONTENT, kidx));
                if (vBranch.isDominatedBy_(vRemote)) {
                    kidcsDel.add(kidx);
                }
            }
            return new CausalityResult(KIndex.MASTER, vRemote.sub_(vMaster), kidcsDel, null,
                    vMaster, true);
        } else if (hRemote == null) {
            l.debug("hash not present");
        }

        Version vAddLocal = Version.copyOf(vRemote);
        KIndex kidxApply = null;
        @Nullable Version vApply = null;

        // MASTER branch should be considered first for application of update as opposed
        // to conflict branches when possible (see "@@" below).
        // Hence iterate over ordered KIndices.
        for (KIndex kidx : remoteOA.cas().keySet()) {
            SOCKID kBranch = new SOCKID(soid, CID.CONTENT, kidx);
            Version vBranch = _nvc.getLocalVersion_(kBranch);

            l.debug("{} l {}", kBranch, vBranch);

            if (vRemote.isDominatedBy_(vBranch)) {
                // The local version is newer or the same as the remote version

                // This SOCKID (kBranch) is a local conflict branch of the remotely-received SOID.
                // If it was expelled it would not have been a member of the CA set. Therefore it
                // should always be present.
                checkState(_ds.isPresent_(kBranch), "%s", kBranch);

                l.warn("l - r > 0");

                // No work to be done
                return null;
            }

            // the local version is older or in parallel

            // Computing/requesting hash is expensive so we compute it only
            // when necessary and ensure hash of remote branch is computed
            // only once.
            final boolean isRemoteDominating = vBranch.isDominatedBy_(vRemote);
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
                _computeHash.issueRequest_(soid, vRemote, msg.did(), tk);
                // Once the above call returns, throw so Downloads will restart this Download.
                // The next time through, the peer should send the hash. Since we already
                // computed the local hash, we can compare them.
                throw new ExRestartWithHashComputed("restart dl after hash");
            }

            //noinspection StatementWithEmptyBody
            if (isRemoteDominating || isContentSame_(kBranch, hRemote, tk)) {
                l.debug("content is the same! {} {}", isRemoteDominating, hRemote);
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

            l.debug("kidx: {} vAddLocal: {}", kidx.getInt(), vAddLocal);
        }

        if (kidxApply == null)  {
            // No subordinate branch was found. Create a new branch.
            SortedMap<KIndex, CA> cas = remoteOA.cas();
            kidxApply = cas.isEmpty() ? KIndex.MASTER : cas.lastKey().increment();

            // The local version should be empty since we have no local branch
            vApply = _nvc.getLocalVersion_(new SOCKID(soid, CID.CONTENT, kidxApply));
            checkState(vApply.isZero_(), "%s %s", vApply, soid);
        }

        // To change a version, we must add the diff of what is to be applied, from the version
        // currently stored locally. Thus the value of vAddLocal up to this point represented
        // the entire version to be applied locally. Now we reset it to be the diff for the sake
        // of version arithmetic.
        vAddLocal = vAddLocal.sub_(vApply);

        l.debug("Final vAddLocal: {}, kApply: {}", vAddLocal, kidxApply);
        return new CausalityResult(kidxApply, vAddLocal, kidcsDel, hRemote, vApply, false);
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
                // FIXME: this close logic is probably broken but probably never user anyway...
                is.close();
            }
            l.debug("Read {} hash bytes of {}", hashBytesRead, hashLength);
        }
        return new ContentHash(os.toByteArray());
    }

    /**
     * Delete obsolete branches, update version vectors
     */
    public void updateVersion_(SOCKID k, Version vRemote, CausalityResult res, Trans t)
            throws SQLException, IOException, ExNotFound, ExAborted
    {
        Long rcv = vRemote.unwrapCentral();
        if (rcv != null) {
            Long lcv = _cvdb.getVersion_(k.sidx(), k.oid());
            if (lcv != null && rcv < lcv) throw new ExAborted(k + " version changed");

            OA oa = _ds.getOA_(k.soid());

            l.debug("{} version {} -> {}", k, lcv, rcv);
            _cvdb.setVersion_(k.sidx(), k.oid(), rcv, t);
            _rcdb.deleteUpToVersion_(k.sidx(), k.oid(), rcv, t);
            if (!_rcdb.hasRemoteChange_(k.sidx(), k.oid(), rcv)) {
                // add "remote" content entry for latest version (in case of expulsion)
                CA ca = oa.ca(k.kidx());
                _rcdb.insert_(k.sidx(), k.oid(), rcv, new DID(UniqueID.ZERO), res._hash, ca.length(), t);
            }

            // del branch if needed
            if (!res._kidcsDel.isEmpty()) {
                checkState(res._kidcsDel.size() == 1);
                KIndex kidx = Iterables.getFirst(res._kidcsDel, null);
                checkState(kidx == KIndex.MASTER.increment());
                checkState(kidx != k.kidx());
                if (oa.caNullable(kidx) != null) {
                    l.info("delete branch {}k{}", k.soid(), kidx);
                    _ds.deleteCA_(k.soid(), kidx, t);
                    _ps.newFile_(_ds.resolve_(k.soid()), kidx).delete_(PhysicalOp.APPLY, t);
                } else {
                    l.warn("{} mergeable ca {} disappeared", k.soid(), kidx);
                }
            }
            return;
        }

        // delete branches
        for (KIndex kidxDel : res._kidcsDel) {
            // guaranteed by computeCausalityForContent()'s logic
            checkState(!kidxDel.isMaster());

            SOCKID kDel = new SOCKID(k.socid(), kidxDel);
            Version vDel = _nvc.getLocalVersion_(kDel);

            // guaranteed by computeCausalityForContent()'s logic
            checkState(!vRemote.isDominatedBy_(vDel));

            _bd.deleteBranch_(kDel, vDel, true, t);
        }

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
    }
}
