/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.daemon.core.ds.*;
import com.aerofs.daemon.core.phy.DigestSerializer;
import com.aerofs.daemon.core.store.*;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.google.common.collect.Maps;
import com.google.inject.Injector;

import javax.annotation.Nullable;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

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
    public final DirectoryServiceImpl ds;

    public InMemoryDS(Injector inj)
    {
        sm = inj.getInstance(SIDMap.class);
        sc = inj.getInstance(StoreCreator.class);
        stores = inj.getInstance(Stores.class);
        ds = inj.getInstance(DirectoryServiceImpl.class);
    }

    public static void set(Object o, String k, Object v)
    {
        try {
            DigestSerializer.field(o, k).set(o, v);
        } catch (NoSuchFieldException|IllegalAccessException e) {
            fail();
        }
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
                fail("not found: " + parent + "/" + oid + " " + pParent.append(name));
            }
            OA oa = ds.ds.getOA_(new SOID(parent.sidx(), child));
            assertEquals(type, oa.type());
            if (oid != null) assertEquals(oid, oa.soid().oid());
            return pParent.join(oa.soid(), oa.name());
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
            throws Exception {
        SOID soid = pParent.isEmpty()
                ? new SOID(ds.sm.get_(pParent.sid()), OID.ROOT)
                : pParent.soid();
        expectChildren(ds, soid, pParent, children);
    }

    public static void expectChildren(InMemoryDS ds, SOID soid, ResolvedPath pParent, Obj[] children)
            throws Exception {
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
        protected Content[] cas;

        protected File(@Nullable OID oid, String name, Content... contents)
        {
            super(oid, name, Type.FILE);
            cas = contents;
        }

        @Override
        protected SOID create(InMemoryDS ds, SOID parent, Trans t)
                throws Exception
        {
            // TODO: content
            SOID soid = super.create(ds, parent, t);
            KIndex kidx = KIndex.MASTER;
            for (Content ca : cas) {
                ds.ds.createCA_(soid, kidx, t);
                ds.ds.setCA_(new SOKID(soid, KIndex.MASTER), ca.length, ca.mtime, ca.hash, t);
                kidx = kidx.increment();
            }
            return soid;
        }

        @Override
        public ResolvedPath expect(InMemoryDS ds, ResolvedPath pParent) throws Exception
        {
            ResolvedPath p = super.expect(ds, pParent);
            Map<KIndex, CA> acas = ds.ds.getOA_(p.soid()).cas();
            KIndex kidx = KIndex.MASTER;
            for (Content ca : cas) {
                CA aca = acas.get(kidx);
                assertNotNull("Expected ca missing: " + p.soid() + kidx, aca);
                assertEquals(ca.length, aca.length());
                assertEquals(ca.mtime, aca.mtime());
                assertEquals(ca.hash, ds.ds.getCAHash_(new SOKID(p.soid(), kidx)));
                kidx = kidx.increment();
            }
            assertEquals("Unexpected cas present", cas.length, acas.size());
            return p;
        }
    }

    public static class Content
    {
        protected long length;
        protected long mtime;
        protected ContentHash hash;

        protected Content(long length, long mtime, ContentHash hash)
        {
            this.length = length;
            this.mtime = mtime;
            this.hash = hash;
        }
    }

    private static class Anchor extends Obj
    {
        private SID sid;
        protected Obj[] children;

        protected Anchor(@Nullable SID sid, String name, Obj... children)
        {
            super(sid != null ? SID.storeSID2anchorOID(sid) : null, name, Type.ANCHOR);
            this.sid = sid;
            this.children = children;
        }

        @Override
        protected SOID create(InMemoryDS ds, SOID parent, Trans t)
                throws Exception
        {
            SOID soid = super.create(ds, parent, t);
            ds.sc.addParentStoreReference_(sid, parent.sidx(), name, t);
            SIndex sidx = ds.sm.get_(sid);
            for (Obj o : children) o.create(ds, new SOID(sidx, OID.ROOT), t);
            return soid;
        }

        @Override
        public ResolvedPath expect(InMemoryDS ds, ResolvedPath pParent) throws Exception {
            ResolvedPath p = super.expect(ds, pParent);
            SIndex sidx = ds.sm.getNullable_(sid);
            if (sidx != null) {
                expectChildren(ds, p.substituteLastSOID(new SOID(sidx, OID.ROOT)), children);
            } else {
                assertEquals(0, children.length);
            }
            return p;
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

    public static Obj file(String name, OID oid, Content... c)
    {
        return new File(oid, name, c);
    }

    public static Content content(byte[] b, long mtime)
    {
        return content(b.length, mtime, new ContentHash(BaseSecUtil.hash(b)));
    }

    public static Content content(long length, long mtime, ContentHash h)
    {
        return new Content(length, mtime, h);
    }

    public static Obj anchor(String name, OID oid, Obj... children)
    {
        return new Anchor(SID.folderOID2convertedStoreSID(oid), name, children);
    }
}
