/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris;

import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.core.AntiEntropy;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.collector.Collector;
import com.aerofs.daemon.core.collector.SenderFilters;
import com.aerofs.daemon.core.ds.AbstractPathResolver;
import com.aerofs.daemon.core.ds.DirectoryServiceImpl;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.expel.LogicalStagingArea;
import com.aerofs.daemon.core.multiplicity.multiuser.MultiuserPathResolver;
import com.aerofs.daemon.core.multiplicity.singleuser.SingleuserPathResolver;
import com.aerofs.daemon.core.multiplicity.singleuser.SingleuserStoreHierarchy;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.polaris.db.MetaChangesDatabase;
import com.aerofs.daemon.core.polaris.fetch.ChangeFetchScheduler;
import com.aerofs.daemon.core.polaris.fetch.ChangeNotificationSubscriber;
import com.aerofs.daemon.core.polaris.fetch.ContentFetcher;
import com.aerofs.daemon.core.polaris.submit.ContentChangeSubmitter;
import com.aerofs.daemon.core.polaris.submit.MetaChangeSubmitter;
import com.aerofs.daemon.core.polaris.submit.SubmissionScheduler;
import com.aerofs.daemon.core.status.PauseSync;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.core.store.StoreCreationOperators;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.daemon.core.store.Stores;
import com.aerofs.daemon.lib.db.AliasDatabase;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.IPulledDeviceDatabase;
import com.aerofs.daemon.lib.db.MetaDatabase;
import com.aerofs.daemon.lib.db.SIDDatabase;
import com.aerofs.daemon.lib.db.StoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Maps;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * "Real" DirectoryService and associated store wiring operating on an in-memory DB
 *
 * Using a real in-memory object tree has many advantages over mocking:
 *      - it's less likely to be subtly inaccurate
 *      - it's a LOT easier to test code that expect to be able to read its own writes
 */
public class InMemoryDS
{
    public final SIDMap sm;
    public final Stores stores;
    public final StoreCreator sc;
    public final DirectoryServiceImpl ds = new DirectoryServiceImpl();
    public final StoreCreationOperators sco = new StoreCreationOperators();
    public final StoreDeletionOperators sdo = new StoreDeletionOperators();

    @SuppressWarnings("unchecked")
    public InMemoryDS(CoreDBCW dbcw, CfgUsePolaris usePolaris, IPhysicalStorage ps, UserID user)
    {
        sm = new SIDMap(new SIDDatabase(dbcw));
        MetaDatabase mdb = new MetaDatabase(dbcw);
        StoreDatabase sdb = new StoreDatabase(dbcw);
        MetaChangesDatabase mcdb = new MetaChangesDatabase(dbcw, sco, sdo);
        ChangeEpochDatabase cedb = new ChangeEpochDatabase(dbcw);
        TransManager tm = mock(TransManager.class);
        StoreHierarchy sh;

        AbstractPathResolver.Factory resolver;

        if (user.isTeamServerID()) {
            sh = new StoreHierarchy(sdb);
            resolver = new MultiuserPathResolver.Factory(sm, sm);
        } else {
            sh = new SingleuserStoreHierarchy(sdb);
            resolver = new SingleuserPathResolver.Factory((SingleuserStoreHierarchy)sh, sm, sm);
        }

        sc = new StoreCreator(mdb, sm, sh, ps, mock(LogicalStagingArea.class), sco, usePolaris);

        SenderFilters.Factory factSF = mock(SenderFilters.Factory.class);
        try {
            when(factSF.create_(any(SIndex.class))).thenReturn(mock(SenderFilters.class));
        } catch (SQLException e) { throw new AssertionError(); }
        Collector.Factory factCollector = mock(Collector.Factory.class);
        try {
            when(factCollector.create_(any(SIndex.class))).thenReturn(mock(Collector.class));
        } catch (SQLException e) { throw new AssertionError(); }
        ChangeFetchScheduler.Factory factCFS = mock(ChangeFetchScheduler.Factory.class);
        when(factCFS.create(any(SIndex.class))).thenReturn(mock(ChangeFetchScheduler.class));
        SubmissionScheduler.Factory<MetaChangeSubmitter> factMCSS
                = mock(SubmissionScheduler.Factory.class);
        when(factMCSS.create(any(SIndex.class))).thenReturn(mock(SubmissionScheduler.class));
        SubmissionScheduler.Factory<ContentChangeSubmitter> factCCSS
                = mock(SubmissionScheduler.Factory.class);
        when(factCCSS.create(any(SIndex.class))).thenReturn(mock(SubmissionScheduler.class));
        ContentFetcher.Factory factCF = mock(ContentFetcher.Factory.class);
        when(factCF.create_(any(SIndex.class))).thenReturn(mock(ContentFetcher.class));

        Store.Factory factStore  = new Store.Factory(factSF, factCollector,
                mock(AntiEntropy.class), mock(Devices.class), mock(IPulledDeviceDatabase.class),
                cedb, mock(ChangeNotificationSubscriber.class), factCFS, factMCSS, factCCSS, factCF,
                mock(PauseSync.class));
        stores = new Stores(sh, sm, factStore, new MapSIndex2Store(), sdo);

        ds.inject_(mdb, new MapAlias2Target(new AliasDatabase(dbcw)), tm, sm, sm, sdo, resolver);
    }

    public static class Obj
    {
        private @Nullable OID oid;
        protected final String name;
        protected final OA.Type type;

        protected Obj(@Nullable OID oid, String name, OA.Type type)
        {
            this.oid = oid;
            this.type = type;
            this.name = name;
        }

        protected SOID create(InMemoryDS ds, SOID parent, Trans t)
                throws Exception
        {
            if (oid == null) oid = OID.generate();
            ds.ds.createOA_(type, parent.sidx(), oid, parent.oid(), name, t);
            return new SOID(parent.sidx(), oid);
        }

        public ResolvedPath expect(InMemoryDS ds, ResolvedPath pParent) throws Exception
        {
            SOID parent = pParent.isEmpty()
                    ? new SOID(ds.sm.get_(pParent.sid()), OID.ROOT)
                    : pParent.soid();
            OID child = ds.ds.getChild_(parent.sidx(), parent.oid(), name);
            if (child == null) {
                fail("not found: " + pParent.append(name));
            }
            OA oa = ds.ds.getOA_(new SOID(parent.sidx(), child));
            assertEquals(type, oa.type());
            if (oid != null) assertEquals(oid, oa.soid().oid());
            return pParent.join(oa);
        }
    }

    private static class Folder extends Obj
    {
        private final Obj[] children;

        protected Folder(@Nullable OID oid, String name, Obj... children)
        {
            super(oid, name,Type.DIR);
            this.children = children;
        }

        @Override
        protected SOID create(InMemoryDS ds, SOID parent, Trans t)
                throws Exception
        {
            SOID soid = super.create(ds, parent, t);
            for (Obj o : children) o.create(ds, soid, t);
            return soid;
        }

        @Override
        public ResolvedPath expect(InMemoryDS ds, ResolvedPath pParent) throws Exception
        {
            ResolvedPath p = super.expect(ds, pParent);
            expectChildren(ds, p, children);
            return p;
        }
    }

    public static void expectChildren(InMemoryDS ds, ResolvedPath pParent, Obj[] children)
            throws Exception
    {
        SOID soid = pParent.isEmpty()
                ? new SOID(ds.sm.get_(pParent.sid()), OID.ROOT)
                : pParent.soid();

        Map<String, OA> extra = Maps.newHashMap();
        for (OID c : ds.ds.getChildren_(soid)) {
            if (c.isTrash()) continue;
            OA oa = ds.ds.getOA_(new SOID(soid.sidx(), c));
            extra.put(oa.name(), oa);
        }

        for (Obj o : children) {
            o.expect(ds, pParent);
            extra.remove(o.name);
        }

        if (extra.isEmpty()) return;

        fail("Unexpected children:\n" + extra.entrySet().stream()
                .map(e -> e.getValue().soid() + " " + e.getValue().type() +  " " + e.getKey())
                .reduce((a, b) -> a + "\n" + b));
    }

    private static class File extends Obj
    {
        protected File(@Nullable OID oid, String name)
        {
            super(oid, name, Type.FILE);
        }

        @Override
        protected SOID create(InMemoryDS ds, SOID parent, Trans t)
                throws Exception
        {
            // TODO: content
            return super.create(ds, parent, t);
        }

        @Override
        public ResolvedPath expect(InMemoryDS ds, ResolvedPath pParent) throws Exception
        {
            ResolvedPath p = super.expect(ds, pParent);
            // TODO: content
            return p;
        }
    }

    private static class Anchor extends Obj
    {
        protected Anchor(@Nullable SID sid, String name)
        {
            super(sid != null ? SID.storeSID2anchorOID(sid) : null, name, Type.ANCHOR);
        }

        @Override
        protected SOID create(InMemoryDS ds, SOID parent, Trans t)
                throws Exception
        {
            SOID soid = super.create(ds, parent, t);
            ds.sc.addParentStoreReference_(SID.anchorOID2storeSID(soid.oid()), parent.sidx(), name, t);
            return soid;
        }
    }

    public class Root
    {
        final SID sid;
        private final Obj[] children;

        protected Root(SID sid, Obj... children)
        {
            this.sid = sid;
            this.children = children;
        }

        protected void create(InMemoryDS ds, Trans t)
                throws Exception
        {
            if (ds.sm.getNullable_(sid) == null) ds.sc.createRootStore_(sid, "", t);
            for (Obj o : children) o.create(ds, new SOID(ds.sm.get_(sid), OID.ROOT), t);
        }

        public void expect(InMemoryDS ds) throws Exception
        {
            expectChildren(ds, ResolvedPath.root(sid), children);
        }
    }

    public void create(SID sid, Obj... chilren) throws Exception
    {
        new Root(sid, chilren).create(this, mock(Trans.class));
    }

    public void expect(SID sid, Obj... children) throws Exception
    {
        new Root(sid, children).expect(this);
    }

    public static Obj folder(String name, Obj... children)
    {
        return folder(name, null, children);
    }

    public static Obj folder(String name, OID oid, Obj... children)
    {
        return new Folder(oid, name, children);
    }

    public static Obj file(String name)
    {
        return file(name, null);
    }

    public static Obj file(String name, OID oid)
    {
        return new File(oid, name);
    }

    public static Obj anchor(String name)
    {
        return new Anchor(SID.folderOID2convertedStoreSID(OID.generate()), name);
    }
}
