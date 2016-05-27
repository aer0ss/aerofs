/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.mock.logical;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.*;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.labeling.L;
import com.aerofs.lib.ClientParam;
import com.aerofs.lib.Path;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.junit.Assert;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.annotation.Nullable;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Helper class to setup a mock logical object tree
 *
 * This class supersedes the old MockRoot / MockAnchor / MockDir / MockFile class and should be
 * used in new tests as it provides more functionality (e.g. mutability, sync status support...)
 * and offers a fluent API.
 *
 * To avoid code duplication, ease porting and because some people might prefer the old API
 * style, MockRoot and its ilk are now implemented as a thin wrapper of MockDS.
 *
 * Usage:
 *      MockDS mds = new MockDS(ds, sid2sidx, sidx2sid);
 *      mds.root()
 *              .dir("foo")
 *                      .anchor("bar")
 *                              .file("hello").parent()
 *                              .file("world").parent().parent()
 *                      .dir("baz")
 *
 * For more advanced examples, see TestAggregateSyncStatus.java
 */
public class MockDS
{
    private final DirectoryService _ds;
    private final @Nullable IMapSID2SIndex _sid2sidx;
    private final @Nullable IMapSIndex2SID _sidx2sid;

    private int _nextSidx;
    private final SID _rootSID;

    private final Map<SIndex, SIndex> _parentStore = Maps.newHashMap();
    private final Map<SIndex, Set<SIndex>> _childStores = Maps.newHashMap();

    private final Map<SID, MockDSRoot> _roots = Maps.newHashMap();

    public MockDS(SID rootSID, DirectoryService ds) throws  Exception
    {
        this(rootSID, ds, null, null);
    }

    public MockDS(SID rootSID, DirectoryService ds, @Nullable IMapSID2SIndex sid2sidx,
            @Nullable IMapSIndex2SID sidx2sid) throws  Exception
    {
        this(rootSID, ds, sid2sidx, sidx2sid, null);
    }

    public MockDS(SID rootSID, DirectoryService ds, @Nullable IMapSID2SIndex sid2sidx,
            @Nullable IMapSIndex2SID sidx2sid, @Nullable StoreHierarchy stores) throws  Exception
    {
        _ds = ds;
        _sid2sidx = sid2sidx;
        _sidx2sid = sidx2sid;

        _nextSidx = 1;
        _rootSID = rootSID;

        when(ds.resolveNullable_(any(Path.class))).thenAnswer(
                invocation -> resolve((Path)invocation.getArguments()[0]));

        when(_ds.hasChildren_(any(SOID.class))).thenAnswer(invocation -> {
            final SOID soid = (SOID)invocation.getArguments()[0];
            return !_ds.getChildren_(soid).isEmpty();
        });

        when(_ds.listChildren_(any(SOID.class))).thenAnswer(invocation -> {
            final SOID soid = (SOID)invocation.getArguments()[0];
            return new IDBIterator<OID>() {
                int i = -1;
                boolean closed = false;
                final ImmutableList<OID> oids = ImmutableList.copyOf(_ds.getChildren_(soid));

                @Override
                public OID get_() throws SQLException
                {
                    return oids.get(i);
                }

                @Override
                public boolean next_()
                {
                    return ++i < oids.size();
                }

                @Override
                public void close_()
                {
                    closed = true;
                }

                @Override
                public boolean closed_()
                {
                    return closed;
                }
            };
        });

        if (stores != null) {
            when(stores.getAll_()).thenAnswer(new Answer<Object>() {
                @Override
                public Object answer(InvocationOnMock invocation)
                {
                    return _childStores.keySet();
                }
            });
            when(stores.getChildren_(any(SIndex.class))).thenAnswer(new Answer<Object>() {
                @Override
                public Object answer(InvocationOnMock invocation)
                {
                    return _childStores.get(invocation.getArguments()[0]);
                }
            });
            when(stores.getParents_(any(SIndex.class))).thenAnswer(new Answer<Object>() {
                @Override
                public Object answer(InvocationOnMock invocation)
                {
                    SIndex sidx = (SIndex)invocation.getArguments()[0];
                    SIndex parent = _parentStore.get(sidx);
                    return parent != null ? Collections.singleton(parent) : Collections.emptySet();
                }
            });
        }
    }

    public SOID resolve(Path path)
    {
        MockDSRoot r = _roots.get(path.sid());
        if (r == null) return null;

        MockDSObject o = r;
        for (String elem : path.elements()) {
            if (o != null && o instanceof MockDSAnchor) {
                o = ((MockDSAnchor)o)._root;
            }
            if (o == null || !(o instanceof MockDSDir)) {
                Loggers.getLogger(MockDS.class).warn("not a dir");
                return null;
            }
            o = ((MockDSDir)o)._children.get(elem);
            Loggers.getLogger(MockDS.class).warn("  {} -> {}", elem, o != null ? o._soid : null);
        }
        return o != null ? o.soid() : null;
    }

    /**
     * Mock SID<->SIndex mapping
     */
    private void mockSIDMap(SID sid, SIndex sidx) throws Exception
    {
        _childStores.put(sidx, Sets.<SIndex>newHashSet());

        if (_sid2sidx != null) {
            SettableFuture<SIndex> f = SettableFuture.create();
            f.set(sidx);
            when(_sid2sidx.wait_(sid)).thenReturn(f);
            when(_sid2sidx.getNullable_(sid)).thenReturn(sidx);
            when(_sid2sidx.getThrows_(sid)).thenReturn(sidx);
            when(_sid2sidx.get_(sid)).thenReturn(sidx);
            when(_sid2sidx.getLocalOrAbsentNullable_(sid)).thenReturn(sidx);
        }
        if (_sidx2sid != null) {
            when(_sidx2sid.getNullable_(sidx)).thenReturn(sid);
            when(_sidx2sid.getThrows_(sidx)).thenReturn(sid);
            when(_sidx2sid.get_(sidx)).thenReturn(sid);
        }
    }

    /**
     * Base class for mocked objects (file, folder, anchor)
     */
    public class MockDSObject
    {
        protected String _name;
        protected final SOID _soid;
        protected final OA _oa;

        protected MockDSDir _parent;

        MockDSObject(String name, MockDSDir parent) throws Exception
        {
            this(name, parent, false);
        }

        MockDSObject(String name, MockDSDir parent, Boolean expelled) throws Exception
        {
            this(name, parent, expelled,
                 new SOID(parent.soid().sidx(), new OID(UniqueID.generate())));
        }

        MockDSObject(String name, MockDSDir parent, Boolean expelled, SOID soid)
                throws Exception
        {
            _name = name;
            _soid = soid;
            _parent = parent;

            // basic DS mocking

            boolean root = this instanceof MockDSRoot;

            ////////
            // mock OA

            _oa = mock(OA.class);
            when(_oa.soid()).thenReturn(_soid);
            when(_oa.name()).thenReturn(_name);
            boolean parentExpelled = _parent != null &&_parent._oa.isExpelled();
            when(_oa.isExpelled()).thenReturn(expelled || parentExpelled);
            when(_oa.isSelfExpelled()).thenReturn(expelled);
            when(_oa.parent()).thenReturn(_soid.oid().isRoot() ? _soid.oid() : _parent._soid.oid());
            when(_oa.synced()).thenReturn(true);
            when(_oa.oosChildren()).thenReturn(0L);

            ////////
            // wire services

            when(_ds.getOA_(eq(_soid))).thenReturn(_oa);
            when(_ds.getOANullable_(eq(_soid))).thenReturn(_oa);
            when(_ds.getOANullableNoFilter_(eq(_soid))).thenReturn(_oa);
            when(_ds.getAliasedOANullable_(eq(_soid))).thenReturn(_oa);

            // The path of a root object should be resolved into the anchor's SOID. So we skip
            // mocking the path resolution for roots.
            if (!root) {
                when(_ds.resolve_(_oa)).thenAnswer(new Answer<ResolvedPath>()
                {
                    @Override
                    public ResolvedPath answer(InvocationOnMock invocation)
                            throws Throwable
                    {
                        return resolved();
                    }
                });
            }
        }

        private ResolvedPath resolved()
        {
            List<SOID> soids = Lists.newArrayList();
            List<String> elems = Lists.newArrayList();

            MockDSObject obj = this;

            do {
                if (obj._name.equals(OA.ROOT_DIR_NAME)) {
                    if (L.isMultiuser()) break;
                    Preconditions.checkState(obj._parent instanceof MockDSAnchor);
                } else {
                    soids.add(obj._soid);
                    elems.add(obj._name);
                }
            } while (!((obj = obj._parent) instanceof MockDSRoot));

            return new ResolvedPath(getPath().sid(), Lists.reverse(soids), Lists.reverse(elems));
        }

        public SOID soid()
        {
            return _soid;
        }

        public OA oa()
        {
            return _oa;
        }

        public MockDSDir parent()
        {
            /*
             * The only object with a null parent should be the root dir of the root store
             * and in a real object tree it is its own parent
             */
            return _parent != null ? _parent : (MockDSDir)this;
        }

        public Path getPath()
        {
            Path p = _parent.getPath();
            return _soid.oid().isRoot() ? p : p.append(_name);
        }

        public void move(MockDSDir newParent, String newName,
                Trans t, IDirectoryServiceListener... listeners) throws Exception
        {
            assert _parent != null;
            assert newParent != this;

            Path oldPath = getPath();
            MockDSDir oldParent = _parent;

            if (newParent != oldParent) {
                _parent.remove(this);
            }

            // must change name after removing from parent
            _name = newName;
            when(_oa.name()).thenReturn(newName);

            if (newParent != oldParent) {
                newParent.add(this);
                _parent = newParent;
                when(_oa.parent()).thenReturn(_parent.soid().oid());
            }

            Path newPath = getPath();

            if (_ds.isTrashOrDeleted_(_parent.soid())) {
                for (IDirectoryServiceListener listener : listeners)
                    listener.objectDeleted_(_soid, oldParent.soid().oid(), oldPath, t);

            } else {
                for (IDirectoryServiceListener listener : listeners)
                    listener.objectMoved_(_soid, oldParent.soid().oid(), _parent.soid().oid(),
                            oldPath, newPath, t);
            }
        }

        public void delete(Trans t, IDirectoryServiceListener... listeners) throws Exception
        {
            // deleting is moving to the trash of the current store
            MockDSDir d = _parent;
            while (d != null && !(d instanceof MockDSAnchor || d instanceof MockDSRoot)) {
                d = d._parent;
            }

            MockDSDir trash = d instanceof MockDSAnchor
                    ? ((MockDSAnchor)d)._trash : ((MockDSRoot)d)._trash;

            move(trash, _soid.oid().toStringFormal(), t,  listeners);
        }
    }

    public class MockDSFile extends MockDSObject
    {
        private final SortedMap<KIndex, CA> cas = Maps.newTreeMap();

        MockDSFile(String name, MockDSDir parent) throws Exception
        {
            this(name, parent, 1);
        }

        MockDSFile(String name, MockDSDir parent, Integer branches) throws Exception
        {
            super(name, parent, branches < 0);
            init(branches);
        }

        MockDSFile(String name, MockDSDir parent, Integer branches, SOID soid) throws Exception
        {
            super(name, parent, branches < 0, soid);
            init(branches);
        }

        void init(int branches) throws Exception
        {
            when(_oa.type()).thenReturn(Type.FILE);
            when(_oa.isFile()).thenReturn(true);
            when(_oa.isDir()).thenReturn(false);
            when(_oa.isAnchor()).thenReturn(false);

            if (!_oa.isExpelled() && branches > 0) {
                // add CAs
                int kMaster = KIndex.MASTER.getInt();
                for (int i = kMaster; i < kMaster + branches; ++i) {
                    CA ca = mock(CA.class);
                    cas.put(new KIndex(i), ca);
                }
                when(_oa.fidNoExpulsionCheck()).thenReturn(new FID(UniqueID.generate().getBytes()));
            }
            when(_oa.casNoExpulsionCheck()).thenReturn(cas);
        }

        public MockDSFile caMaster(long length, long mtime)
        {
            return ca(KIndex.MASTER, length, mtime);
        }

        public MockDSFile ca(KIndex kidx, long length, long mtime)
        {
            CA ca = cas.get(kidx);
            when(ca.length()).thenReturn(length);
            when(ca.mtime()).thenReturn(mtime);
            return this;
        }
    }

    public class MockDSDir extends MockDSObject
    {
        final Map<String, MockDSObject> _children = Maps.newTreeMap();

        MockDSDir(String name, MockDSDir parent) throws Exception
        {
            super(name, parent);
            init();
        }

        MockDSDir(String name, MockDSDir parent, Boolean expelled) throws Exception
        {
            super(name, parent, expelled);
            init();
        }

        MockDSDir(String name, MockDSDir parent, SOID soid) throws Exception
        {
            this(name, parent, false, soid);
        }

        MockDSDir(String name, MockDSDir parent, Boolean expelled, SOID soid) throws Exception
        {
            super(name, parent, expelled, soid);

            init();
        }

        private void init() throws Exception
        {
            when(_oa.type()).thenReturn(Type.DIR);
            when(_oa.isFile()).thenReturn(false);
            when(_oa.isDir()).thenReturn(true);
            when(_oa.isDirOrAnchor()).thenReturn(true);
            when(_oa.isAnchor()).thenReturn(false);

            final MockDSDir _this = this;
            when(_ds.getChild_(eq(_soid.sidx()), eq(_soid.oid()), anyString()))
                    .then(invocation -> {
                        Object[] args = invocation.getArguments();
                        String name = (String)args[2];
                        MockDSObject o = _this._children.get(name);
                        return o != null ? o.soid().oid() : null;
                    });

            if (!_oa.isExpelled() && !_soid.oid().isRoot()) {
                when(_oa.fidNoExpulsionCheck()).thenReturn(new FID(UniqueID.generate().getBytes()));
            }
        }

        @Override
        public Path getPath()
        {
            if (L.isMultiuser() && _soid.oid().isRoot() && _parent instanceof MockDSAnchor) {
                return Path.root(SID.anchorOID2storeSID(_parent._soid.oid()));
            }
            return super.getPath();
        }

        @Override
        public void delete(Trans t, IDirectoryServiceListener... listeners) throws Exception
        {
            // recursively delete children
            for (MockDSObject o : _children.values()) {
                o.delete(t, listeners);
            }

            // undo DS mocking
            when(_ds.getChild_(eq(_soid.sidx()), eq(_soid.oid()), any(String.class)))
                    .thenReturn(null);

            super.delete(t, listeners);
        }

        @SuppressWarnings("rawtypes")
        private <T extends MockDSObject> T child(String name, Class<T> c, Object... param)
                throws Exception
        {
            MockDSObject o = _children.get(name);
            if (o != null) {
                assert o.getClass().equals(c);
                return c.cast(o);
            }
            try {
                Class<?>[] cl = new Class[3 + param.length];
                cl[0] = MockDS.class;
                cl[1] = String.class;
                cl[2] = MockDSDir.class;
                Object[] pl = new Object[3 + param.length];
                pl[0] = MockDS.this;
                pl[1] = name;
                pl[2] = this;
                for (int i = 0; i < param.length; ++i) {
                    cl[3 + i] = param[i].getClass();
                    pl[3 + i] = param[i];
                }
                T no = c.getDeclaredConstructor(cl).newInstance(pl);
                add(no);
                return no;
            } catch (Exception e) {
                e.printStackTrace();
                assert false : e.getLocalizedMessage();
                return null;
            }
        }

        void add(MockDSObject child) throws Exception
        {
            _children.put(child._name, child);
            mockChildren();
        }

        void remove(MockDSObject child) throws Exception
        {
            Preconditions.checkState(_children.remove(child._name) == child, "%s %s", child._name, _children);
            mockChildren();
        }

        void mockChildren() throws Exception
        {
            Set<OID> childrenOID = Sets.newHashSet();
            for (MockDSObject c : _children.values()) {
                childrenOID.add(c.soid().oid());
            }
            when(_ds.getChildren_(eq(_soid))).thenReturn(childrenOID);
        }

        public MockDSDir cd(String name)
        {
            MockDSObject o = _children.get(name);
            Assert.assertNotNull(o);
            Assert.assertTrue(o instanceof MockDSDir);
            return o instanceof MockDSAnchor ? ((MockDSAnchor)o)._root : (MockDSDir)o;
        }

        public boolean hasChild(String name)
        {
            return _children.containsKey(name);
        }

        public MockDSObject child(String name)
        {
            MockDSObject o = _children.get(name);
            Assert.assertNotNull(o);
            return o;
        }

        public MockDSFile file(String name) throws Exception
        {
            return child(name, MockDSFile.class);
        }

        public MockDSFile file(String name, int branches) throws Exception
        {
            return child(name, MockDSFile.class, branches);
        }

        public MockDSFile file(SOID soid, String name, int branches) throws Exception
        {
            return child(name, MockDSFile.class, branches, soid);
        }

        public MockDSDir dir(String name) throws Exception
        {
            return child(name, MockDSDir.class);
        }

        public MockDSDir dir(String name, boolean expelled) throws Exception
        {
            return child(name, MockDSDir.class, expelled);
        }

        public MockDSDir dir(SOID soid, String name, boolean expelled) throws Exception
        {
            return child(name, MockDSDir.class, expelled, soid);
        }

        public MockDSAnchor anchor(String name) throws Exception
        {
            return child(name, MockDSAnchor.class);
        }

        public MockDSAnchor anchor(String name, boolean expelled) throws Exception
        {
            return child(name, MockDSAnchor.class, expelled);
        }
    }

    public class MockDSAnchor extends MockDSDir
    {
        MockDSDir _root;
        MockDSDir _trash;

        MockDSAnchor(String name, MockDSDir parent) throws Exception
        {
            this(name, parent, false);
        }

        MockDSAnchor(String name, MockDSDir parent, Boolean expelled) throws Exception
        {
            super(name, parent, expelled,
                    new SOID(parent.soid().sidx(), SID.storeSID2anchorOID(SID.generate())));

            // DS mocking
            when(_oa.type()).thenReturn(Type.ANCHOR);
            when(_oa.isDir()).thenReturn(false);
            when(_oa.isAnchor()).thenReturn(true);

            SIndex sidx = new SIndex(_nextSidx++);
            assert _sidx2sid == null || _sidx2sid.getNullable_(sidx) == null : sidx;

            // SIDMap mocking
            mockSIDMap(SID.anchorOID2storeSID(_oa.soid().oid()), sidx);
            if (!expelled) {
                _parentStore.put(sidx, _oa.soid().sidx());
                _childStores.get(_oa.soid().sidx()).add(sidx);
            }

            // create root dir
            _root = new MockDSDir(OA.ROOT_DIR_NAME, this, new SOID(sidx, OID.ROOT));
            _trash = new MockDSDir(ClientParam.TRASH, _root, true, new SOID(sidx, OID.TRASH));
            _root._children.put(ClientParam.TRASH, _trash);

            if (!expelled) {
                when(_ds.followAnchorNullable_(_oa)).thenReturn(_root.soid());
            } else {
                when(_ds.followAnchorNullable_(_oa)).thenReturn(null);
            }
        }

        @Override
        public void delete(Trans t, IDirectoryServiceListener... listeners) throws Exception
        {
            // recursively delete children
            _root.delete(t, listeners);

            when(_ds.followAnchorNullable_(_oa)).thenReturn(null);

            // undo DS mocking
            super.delete(t,  listeners);
        }

        @Override
        public MockDSFile file(String name) throws Exception
        {
            return _root.file(name);
        }

        @Override
        public MockDSFile file(String name, int branches) throws Exception
        {
            return _root.file(name, branches);
        }

        @Override
        public MockDSDir dir(String name) throws Exception
        {
            return _root.dir(name);
        }

        @Override
        public MockDSDir dir(String name, boolean  expelled) throws Exception
        {
            return _root.dir(name, expelled);
        }

        @Override
        public MockDSAnchor anchor(String name) throws Exception
        {
            return _root.anchor(name);
        }

        @Override
        public MockDSAnchor anchor(String name, boolean expelled) throws Exception
        {
            return _root.anchor(name, expelled);
        }

        public MockDSDir root()
        {
            return _root;
        }
    }

    public class MockDSRoot extends MockDSDir
    {
        SID _sid;
        MockDSDir _trash;

        public MockDSRoot(SID sid, SIndex sidx) throws Exception
        {
            super(OA.ROOT_DIR_NAME, null, new SOID(sidx, OID.ROOT));

            _sid = sid;
            _trash = new MockDSDir(ClientParam.TRASH, this, true, new SOID(sidx, OID.TRASH));
            _children.put(ClientParam.TRASH, _trash);

            when(_ds.resolve_(_oa)).thenReturn(ResolvedPath.root(_sid));

            mockSIDMap(sid, sidx);
        }

        @Override
        public Path getPath()
        {
            return Path.root(_sid);
        }
    }

    public MockDSDir root() throws Exception
    {
        return root(_rootSID);
    }

    public MockDSDir root(SID sid) throws Exception
    {
        MockDSRoot root = _roots.get(sid);
        if (root == null) {
            root = new MockDSRoot(sid, new SIndex(_nextSidx++));
            _roots.put(sid, root);
        }
        return root;
    }

    public MockDSDir cd(Path path) throws Exception
    {
        MockDSDir d = root();
        for (String e : path.elements()) {
            d = d.cd(e);
            Assert.assertNotNull(e + " not dir in " + path, d);
        }
        return d;
    }

    /**
     * State change triggers
     */

    public void touch(String p, Trans t, IDirectoryServiceListener... listeners) throws Exception
    {
        Path path = Path.fromString(_rootSID, p);
        MockDSDir d = cd(path.removeLast());
        MockDSFile f = d.file(path.last());
        for (IDirectoryServiceListener listener : listeners)
            listener.objectCreated_(f.soid(), d.soid().oid(), path, t);
    }

    public void mkdir(String p, Trans t, IDirectoryServiceListener... listeners) throws Exception
    {
        Path path = Path.fromString(_rootSID, p);
        MockDSDir d = cd(path.removeLast());
        MockDSDir f = d.dir(path.last());
        for (IDirectoryServiceListener listener : listeners)
            listener.objectCreated_(f.soid(), d.soid().oid(), path, t);
    }

    public void delete(String p, Trans t, IDirectoryServiceListener... listeners) throws Exception
    {
        Path path = Path.fromString(_rootSID, p);
        MockDSDir d = cd(path.removeLast());
        MockDSObject c = d.child(path.last());
        c.delete(t, listeners);
    }

    public void move(String org, String dst, Trans t, IDirectoryServiceListener... listeners)
            throws Exception
    {
        Path from = Path.fromString(_rootSID, org);
        Path to = Path.fromString(_rootSID, dst);
        MockDSDir dfrom = cd(from.removeLast());
        MockDSObject c = dfrom.child(from.last());

        MockDSDir dto = cd(to.removeLast());
        if (dto.soid().sidx().equals(c.soid().sidx())) {
            c.move(dto, to.last(), t, listeners);
        } else {
            // TODO: fully accurate copy of branches and expulsion if needed
            dto.file(new SOID(dto.soid().sidx(), c._soid.oid()), to.last(), c.oa().cas().size());
            c.delete(t, listeners);
        }
    }
}
