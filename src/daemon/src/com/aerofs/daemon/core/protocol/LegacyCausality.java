package com.aerofs.daemon.core.protocol;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.object.BranchDeleter;
import com.aerofs.daemon.core.protocol.ContentUpdater.ReceivedContent;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.*;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.SortedMap;

import static com.google.common.base.Preconditions.checkState;

public class LegacyCausality implements Causality {
    private final static Logger l = Loggers.getLogger(LegacyCausality.class);

    private final DirectoryService _ds;
    private final NativeVersionControl _nvc;
    private final BranchDeleter _bd;

    @Inject
    public LegacyCausality(DirectoryService ds, NativeVersionControl nvc, BranchDeleter bd)
    {
        _ds = ds;
        _nvc = nvc;
        _bd = bd;
    }

    /**
     * @return null if not to apply the update
     */
    @Override
    public @Nullable CausalityResult computeCausality_(SOID soid, ReceivedContent content)
            throws Exception
    {
        OA remoteOA = _ds.getOAThrows_(soid);
        if (remoteOA.isExpelled()) throw new ExAborted("expelled " + soid);

        List<KIndex> kidcsDel = Lists.newArrayList();

        if (content.hash == null) {
            l.debug("hash not present");
        } else {
            l.debug("hash included {}", content.hash);
        }

        Version vAddLocal = Version.copyOf(content.vRemote);
        KIndex kidxApply = null;
        @Nullable Version vApply = null;

        // MASTER branch should be considered first for application of update as opposed
        // to conflict branches when possible (see "@@" below).
        // Hence iterate over ordered KIndices.
        for (KIndex kidx : remoteOA.cas().keySet()) {
            SOCKID kBranch = new SOCKID(soid, CID.CONTENT, kidx);
            Version vBranch = _nvc.getLocalVersion_(kBranch);

            l.debug("{} l {}", kBranch, vBranch);

            if (content.vRemote.isDominatedBy_(vBranch)) {
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
            final boolean isRemoteDominating = vBranch.isDominatedBy_(content.vRemote);
            if (!isRemoteDominating && content.hash == null) {
                throw new ExRestartWithHashComputed(kidx);
            }

            //noinspection StatementWithEmptyBody
            if (isRemoteDominating || content.hash.equals(_ds.getCAHash_(new SOKID(soid, kidx)))) {
                l.debug("content is the same! {} {}", isRemoteDominating, content.hash);
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
        return new CausalityResult(kidxApply, vAddLocal, kidcsDel, content.hash, vApply, false);
    }

    CausalityResult contentSame(SOID soid, ReceivedContent content)
            throws SQLException, ExNotFound, ExAborted {
        OA remoteOA = _ds.getOAThrows_(soid);
        if (remoteOA.isExpelled()) throw new ExAborted("expelled " + soid);

        List<KIndex> kidcsDel = Lists.newArrayList();
        // requested remote version has same content as the MASTER version we had when we made
        // the request -> add version to MASTER without any file I/O
        Version vMaster = _nvc.getLocalVersion_(new SOCKID(soid, CID.CONTENT, KIndex.MASTER));

        // cleanup branches dominated by remote
        for (KIndex kidx : remoteOA.cas().keySet()) {
            if (kidx.isMaster()) continue;
            Version vBranch = _nvc.getLocalVersion_(new SOCKID(soid, CID.CONTENT, kidx));
            if (vBranch.isDominatedBy_(content.vRemote)) {
                kidcsDel.add(kidx);
            }
        }
        return new CausalityResult(KIndex.MASTER, content.vRemote.sub_(vMaster), kidcsDel, null,
                vMaster, true);
    }

    @Override
    public void updateVersion_(SOKID sokid, ReceivedContent content, CausalityResult res, Trans t)
            throws SQLException, IOException, ExNotFound, ExAborted
    {
        SOCKID k = new SOCKID(sokid, CID.CONTENT);
        // delete branches
        for (KIndex kidxDel : res._kidcsDel) {
            // guaranteed by computeCausalityForContent()'s logic
            checkState(!kidxDel.isMaster());

            SOCKID kDel = new SOCKID(k.socid(), kidxDel);
            Version vDel = _nvc.getLocalVersion_(kDel);

            // guaranteed by computeCausalityForContent()'s logic
            checkState(!content.vRemote.isDominatedBy_(vDel));

            _bd.deleteBranch_(kDel, vDel, true, t);
        }

        Version vKML = _nvc.getKMLVersion_(k.socid());
        Version vKML_R = vKML.sub_(content.vRemote);
        Version vDelKML = vKML.sub_(vKML_R);

        l.debug("{}: r {}  kml {} -kml {} +l {}", k, content.vRemote, vKML, vDelKML, res._vAddLocal);

        // check if the local version has changed during our pauses
        if (!_nvc.getLocalVersion_(k).isDominatedBy_(res._vLocal)) {
            throw new ExAborted(k + " version changed locally.");
        }

        // update version vectors
        _nvc.deleteKMLVersion_(k.socid(), vDelKML, t);
        _nvc.addLocalVersion_(k, res._vAddLocal, t);
    }
}
