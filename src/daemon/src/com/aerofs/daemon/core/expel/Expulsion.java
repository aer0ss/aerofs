package com.aerofs.daemon.core.expel;

import static com.aerofs.daemon.core.ds.OA.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.IExpulsionDatabase;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.Util;
import static com.aerofs.lib.Util.set;
import static com.aerofs.lib.Util.unset;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This class is the access point of the subsystem that maintains objects' expulsion state.
 *
 * Both a separate table (the exclusion database) and per-object flags (OA.FLAG_EXPELLED_*) are
 * used to remember whether an object is expelled or not. Although they're redundant, the former is
 * for returning all the expelled objects, and the latter is for operations on individual objects.
 * Combining the two would require creation of a new index over the the metadata table, and thus
 * introduce overhead to all object operations.
 */

// FIXME Mark insisted (to Weihan) that the name of this class and its public methods should be
// changed to be more coherent with other classes.

public class Expulsion
{
    private static final Logger l = Loggers.getLogger(Expulsion.class);
    private DirectoryService _ds;
    private IExpulsionDatabase _exdb;
    private ICollectorSequenceDatabase _csdb;
    private NativeVersionControl _nvc;
    private MapSIndex2Store _sidx2s;
    private AdmittedToAdmittedAdjuster _adjA2A;
    private AdmittedToExpelledAdjuster _adjA2E;
    private ExpelledToAdmittedAdjuster _adjE2A;
    private ExpelledToExpelledAdjuster _adjE2E;

    @Inject
    public void inject_(DirectoryService ds, IExpulsionDatabase exdb,
            ExpelledToExpelledAdjuster adjE2E, ExpelledToAdmittedAdjuster adjE2A,
            AdmittedToExpelledAdjuster adjA2E, AdmittedToAdmittedAdjuster adjA2A,
            NativeVersionControl nvc, ICollectorSequenceDatabase csdb, MapSIndex2Store sidx2s)
    {
        _adjE2E = adjE2E;
        _adjE2A = adjE2A;
        _adjA2E = adjA2E;
        _adjA2A = adjA2A;
        _ds = ds;
        _exdb = exdb;
        _nvc = nvc;
        _csdb = csdb;
        _sidx2s = sidx2s;
    }

    /**
     * The method returns the appropriate IExpulsionAdjuster given the previous and current flags
     * of the object. The flags determine the object's expulsion state.
     */
    private IExpulsionAdjuster getAdjuster(int flagsOld, int flagsNew)
    {
        boolean effectiveOld = effectivelyExpelled(flagsOld);
        boolean effectiveNew = effectivelyExpelled(flagsNew);

        if (!effectiveOld && !effectiveNew) {
            return _adjA2A;
        } else if (!effectiveOld && effectiveNew) {
            return _adjA2E;
        } else if (effectiveOld && !effectiveNew) {
            return _adjE2A;
        } else {
            assert effectiveOld && effectiveNew;
            return _adjE2E;
        }
    }

    /**
     * Call this method whenever an object's name or parent has been updated. Even though object
     * name changing alone doesn't affect expulsion state, the method should still be called to
     * rename physical objects. See {@Link IAdjuster} for operational details and parameter list.
     */
    public void objectMoved_(boolean emigrate, PhysicalOp op, SOID soid, ResolvedPath pathOld, Trans t)
            throws SQLException, ExNotFound, IOException, ExNotDir, ExStreamInvalid, ExAlreadyExist
    {
        OA oa = _ds.getOA_(soid);
        int flagsOld = oa.flags();

        // compute the object's flags based on the parent's
        SOID soidParent = new SOID(soid.sidx(), oa.parent());
        OA oaParent = _ds.getOAThrows_(soidParent);
        boolean parentExpelled = oaParent.isExpelled();
        int flagsNew = parentExpelled ? set(flagsOld, FLAG_EXPELLED_INH) :
            unset(flagsOld, FLAG_EXPELLED_INH);

        IExpulsionAdjuster adj = getAdjuster(flagsOld, flagsNew);
        adj.adjust_(emigrate, op, soid, pathOld, flagsNew, t);
    }

    /**
     * This method does the following:
     *
     *   1) sets or unsets FLAG_EXPELLED_ORG to the specified object,
     *   2) adjusts the explusion state of child objects accordingly, and
     *   3) adds or removes the object id to or from the expulsion database.
     *
     * The object has to be a folder or an anchor, otherwise {@code ExNotDir} will be thrown.
     * The method is idempotent if called multiple times with the same parameters.
     */
    public void setExpelled_(boolean expelled, SOID soid, Trans t)
            throws SQLException, ExNotDir, ExStreamInvalid, IOException, ExNotFound, ExAlreadyExist
    {
        OA oa = _ds.getOA_(soid);

        // only folder expulsion is supported
        if (!oa.isDirOrAnchor()) throw new ExNotDir();

        int flagsOld = oa.flags();
        int flagsNew = expelled ? set(flagsOld, FLAG_EXPELLED_ORG) :
            unset(flagsOld, FLAG_EXPELLED_ORG);

        if (flagsNew == flagsOld) return;

        l.debug("set expulsion of " + soid + " with " + expelled);

        if (expelled) _exdb.insertExpelledObject_(soid, t);
        else _exdb.deleteExpelledObject_(soid, t);

        IExpulsionAdjuster adj = getAdjuster(flagsOld, flagsNew);
        ResolvedPath path = _ds.resolve_(oa);
        adj.adjust_(false, PhysicalOp.APPLY, soid, path, flagsNew, t);
    }

    /**
     * @pre the alias object exists locally
     * @param target remote target OID, null if the target is locally present
     */
    public void objectAliased_(@Nonnull OA oaAlias, @Nullable SOID target, Trans t)
            throws SQLException
    {
        // if the alias was explicitly expelled we need to make sure the target takes over:
        // 1) leaving an alias in the expulsion table would lead to assertion failures when
        // the UI tries to list expelled objects
        // 2) not expelling the target would cause the expelled files/folders to reappear, to
        // the extreme confusion/dismay of the user
        if (Util.test(oaAlias.flags(), OA.FLAG_EXPELLED_ORG)) {
            _exdb.deleteExpelledObject_(oaAlias.soid(), t);
            if (target != null) _exdb.insertExpelledObject_(target, t);
        }
    }

    /**
     * N.B. the returned list doesn't contain trash folders, since StoreCreator.java create
     * these folders specially, without calling setExpelled_().
     */
    public IDBIterator<SOID> getExpelledObjects_() throws SQLException
    {
        return _exdb.getExpelledObjects_();
    }

    /**
     * @return true iff the flags indicates that the object is effectively expelled, that is,
     * either the originate expelled bit or inherited expelled bit is set.
     */
    static boolean effectivelyExpelled(int flags)
    {
        return Util.test(flags, FLAG_EXPELLED_ORG_OR_INH);
    }

    /**
     * called when expelled flag is set to the file
     *
     * the caller must update the KML versions of this object on its own
     */
    public void fileExpelled_(SOID soid) throws SQLException
    {
        // TODO right now this method is called from two places. Ideally the call from createMeta
        // should be removed. Afterward, we can move expel/admitFile() to the caller's classes.

        assert _ds.getOA_(soid).isFile();

        /*
         * Intentionally, do not delete DIDs for soid's stored in the pddb;
         * only do it upon file admission.
         * Consider the contrary: the soid's file is to be expelled, so the
         * local peer does not want to download it again, thus the peer does
         * not need the full history of BFOIDs to download an old version of
         * the file. The rest of the files in the store are not necessarily
         * expelled and only need the latest BFOIDs, so should "remember"
         * the DIDs from which they have pulled filters and knowledge.
         */

        /*
         * o del from cs, dl, pre, etc
         */
    }

    // Because huge amount of files may be admitted at once, we batch store operations for these
    // files at the end of the transaction.
    private class StoresWithAdmittedFiles extends AbstractTransListener
    {
        HashSet<SIndex> _sidxs = Sets.newHashSet();

        void add_(SIndex sidx)
        {
            _sidxs.add(sidx);
        }

        @Override
        public void committing_(Trans t) throws SQLException
        {
            for (SIndex sidx : _sidxs) {
                _sidx2s.get_(sidx).resetCollectorFiltersForAllDevices_(t);
            }
        }
    }

    private final TransLocal<StoresWithAdmittedFiles> _tlaf =
            new TransLocal<StoresWithAdmittedFiles>() {
                @Override
                protected StoresWithAdmittedFiles initialValue(Trans t)
                {
                    StoresWithAdmittedFiles swaf = new StoresWithAdmittedFiles();
                    t.addListener_(swaf);
                    return swaf;
                }
            };

    /**
     * called when a file is admitted from the expelled state
     */
    void fileAdmitted_(SOID soid, Trans t) throws SQLException
    {
        assert _ds.getOA_(soid).isFile();

        SOCID socid = new SOCID(soid, CID.CONTENT);

        // Ignore if the content doesn't have KML version. Strictly speaking this is not needed
        // because the collector automatically skips objects with zero KML in the collector-sequence
        // table.
        if (_nvc.getKMLVersion_(socid).isZero_()) return;

        _csdb.insertCS_(socid, t);
        _tlaf.get(t).add_(soid.sidx());
    }
}
