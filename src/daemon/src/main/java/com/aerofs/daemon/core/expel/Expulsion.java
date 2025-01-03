package com.aerofs.daemon.core.expel;

import java.sql.SQLException;
import java.util.List;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.IExpulsionDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
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
    private AdmittedToAdmittedAdjuster _adjA2A;
    private AdmittedToExpelledAdjuster _adjA2E;
    private ExpelledToAdmittedAdjuster _adjE2A;
    private ExpelledToExpelledAdjuster _adjE2E;
    private LogicalStagingArea _sa;

    private final List<IExpulsionListener> _listeners = Lists.newArrayList();

    public interface IExpulsionListener
    {
        void anchorExpelled_(SOID soid, Trans t) throws SQLException;
    }

    @Inject
    public void inject_(DirectoryService ds, IExpulsionDatabase exdb, LogicalStagingArea sa,
            ExpelledToExpelledAdjuster adjE2E, ExpelledToAdmittedAdjuster adjE2A,
            AdmittedToExpelledAdjuster adjA2E, AdmittedToAdmittedAdjuster adjA2A)
    {
        _adjE2E = adjE2E;
        _adjE2A = adjE2A;
        _adjA2E = adjA2E;
        _adjA2A = adjA2A;
        _ds = ds;
        _exdb = exdb;
        _sa = sa;
    }

    public void addListener_(IExpulsionListener listener)
    {
        _listeners.add(listener);
    }

    public ImmutableList<IExpulsionListener> listeners_()
    {
        return ImmutableList.copyOf(_listeners);
    }

    /**
     * @return whether any of the objects in the given path is expelled
     *
     * NB: this differs from {@link OA#isExpelled()} in that the given path
     * may not reflect the current state of the DB wrt parent/child relationships
     */
    public final boolean isExpelled_(List<SOID> soids) throws SQLException
    {
        for (SOID soid : soids) if (_ds.getOA_(soid).isSelfExpelled()) return true;
        return false;
    }

    /**
     * Call this method whenever an object's name or parent has been updated. Even though object
     * name changing alone doesn't affect expulsion state, the method should still be called to
     * rename physical objects. See {@link IExpulsionAdjuster}.
     */
    public void objectMoved_(ResolvedPath pathOld, SOID soid, PhysicalOp op, Trans t)
            throws Exception
    {
        OA oa = _ds.getOA_(soid);

        boolean wasExpelled = isExpelled_(pathOld.soids);
        boolean nowExpelled = oa.isExpelled();

        getAdjuster(wasExpelled, nowExpelled)
                .adjust_(pathOld, oa.soid(), op, t);
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
            throws Exception
    {
        OA oa = _ds.getOA_(soid);

        if (oa.soid().oid().isRoot()) throw new ExBadArgs();

        // only folder expulsion is supported
        if (!oa.isDirOrAnchor()) throw new ExNotDir();

        SOID parent = new SOID(oa.soid().sidx(), oa.parent());
        boolean parentExpelled = _ds.getOA_(parent).isExpelled();
        boolean wasExpelled = parentExpelled || oa.isSelfExpelled();
        boolean nowExpelled = parentExpelled || expelled;

        if (wasExpelled == nowExpelled) return;

        l.info("set expulsion of {} with {}", soid, expelled);

        if (expelled) {
            _exdb.insertExpelledObject_(soid, t);
        } else {
            _exdb.deleteExpelledObject_(soid, t);
        }

        _ds.setExpelled_(oa.soid(), nowExpelled, t);

        ResolvedPath path = _ds.resolve_(oa);
        getAdjuster(wasExpelled, nowExpelled)
                .adjust_(path, oa.soid(), PhysicalOp.APPLY, t);
    }

    /**
     * The method returns the appropriate IExpulsionAdjuster given the previous and current
     * expulsion state of an object
     */
    private IExpulsionAdjuster getAdjuster(boolean wasExpelled, boolean nowExpelled)
    {
        if (wasExpelled) {
            return nowExpelled ? _adjE2E : _adjE2A;
        } else {
            return nowExpelled ? _adjA2E : _adjA2A;
        }
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
        if (oaAlias.isSelfExpelled()) {
            _exdb.deleteExpelledObject_(oaAlias.soid(), t);
            if (target != null) _exdb.insertExpelledObject_(target, t);
        }

        // if the alias was staged, make sure the target is staged
        _sa.objectAliased_(oaAlias.soid(), target, t);
    }

    /**
     * N.B. the returned list doesn't contain trash folders, since StoreCreator.java create
     * these folders specially, without calling setExpelled_().
     */
    public IDBIterator<SOID> getExpelledObjects_() throws SQLException
    {
        return _exdb.getExpelledObjects_();
    }
}
