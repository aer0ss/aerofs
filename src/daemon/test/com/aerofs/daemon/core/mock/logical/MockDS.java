/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.mock.logical;

import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.IDirectoryServiceListener;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.store.DeviceBitMap;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.MapSIndex2DeviceBitMap;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.C;
import com.aerofs.lib.CounterVector;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.UniqueID;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import junit.framework.Assert;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
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
    private final @Nullable MapSIndex2DeviceBitMap _sidx2dbm;

    private final SID _sid;
    private final MockDSDir _root;
    private final MockDSDir _trash;

    /**
     * A Mockito argument matcher for matching Paths in a case-insensitive way (as the
     * DirectoryService does)
     */
    private static class IsEqualPathIgnoringCase extends ArgumentMatcher<Path>
    {
        private final Path _path;

        IsEqualPathIgnoringCase(Path path) {_path = path;}

        // In reality, when passed a path of same name, but different case, the DS returns the SOID
        // for the case that is stored in the DB. This matcher helps reflect that behaviour
        @Override
        public boolean matches(Object path)
        {
            return _path.equalsIgnoreCase((Path) path);
        }
    }

    public MockDS(DirectoryService ds) throws  Exception
    {
        this(ds, null, null, null);
    }

    public MockDS(DirectoryService ds, @Nullable IMapSID2SIndex sid2sidx,
            @Nullable IMapSIndex2SID sidx2sid) throws  Exception
    {
        this(ds, sid2sidx, sidx2sid, null);
    }

    @Inject
    public MockDS(DirectoryService ds, @Nullable IMapSID2SIndex sid2sidx,
            @Nullable IMapSIndex2SID sidx2sid, @Nullable MapSIndex2DeviceBitMap sidx2dbm)
            throws  Exception
    {
        _ds = ds;
        _sid2sidx = sid2sidx;
        _sidx2sid = sidx2sid;
        _sidx2dbm = sidx2dbm;

        // mock root store
        SIndex sidx = new SIndex(1);
        _sid = new SID(UniqueID.generate());
        _root = new MockDSDir(OA.ROOT_DIR_NAME, null, new SOID(sidx, OID.ROOT));
        _trash = new MockDSDir(C.TRASH, _root, true, new SOID(sidx, OID.TRASH));

        mockSIDMap(_sid, sidx);

        // mock path resolution for root
        when(_ds.resolveNullable_(argThat(new IsEqualPathIgnoringCase(new Path()))))
                .thenReturn(_root.soid());
        when(_ds.resolveThrows_(argThat(new IsEqualPathIgnoringCase(new Path()))))
                .thenReturn(_root.soid());

        /*
         * update mocks reactively
         *
         * WARNING: the mock must return new objects on every call because the caller expects to
         * be able to modify the returned object and pass it to other functions which will break
         * in delightfully weird and hard-to-debug ways.
         */

        doAnswer(new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                Object[] args = invocation.getArguments();
                SOID soid = (SOID)args[0];
                final BitVector bv = (BitVector)args[1];
                when(_ds.getSyncStatus_(eq(soid))).thenAnswer(new Answer<BitVector>()
                {
                    @Override
                    public BitVector answer(InvocationOnMock invocation) throws Throwable
                    {
                        return new BitVector(bv);
                    }
                });
                return null;
            }
        }).when(_ds).setSyncStatus_(
                any(SOID.class), any(BitVector.class), any(Trans.class));

        doAnswer(new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                Object[] args = invocation.getArguments();
                SOID soid = (SOID)args[0];
                final CounterVector cv = (CounterVector)args[1];
                when(_ds.getAggregateSyncStatus_(eq(soid))).thenAnswer(new Answer<CounterVector>()
                {
                    @Override
                    public CounterVector answer(InvocationOnMock invocation)
                            throws Throwable
                    {
                        return new CounterVector(cv);
                    }
                });
                return null;
            }
        }).when(_ds).setAggregateSyncStatus_(any(SOID.class), any(CounterVector.class),
                any(Trans.class));

    }

    /**
     * Mock SID<->SIndex mapping
     */
    private void mockSIDMap(SID sid, SIndex sidx)
    {
        if (_sid2sidx != null) {
            when(_sid2sidx.getNullable_(sid)).thenReturn(sidx);
            when(_sid2sidx.get_(sid)).thenReturn(sidx);
        }
        if (_sidx2sid != null) {
            when(_sidx2sid.getNullable_(sidx)).thenReturn(sid);
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

            boolean root = (parent == null);
            Path path = getPath();

            ////////
            // mock OA

            _oa = mock(OA.class);
            when(_oa.soid()).thenReturn(_soid);
            when(_oa.name()).thenReturn(_name);
            when(_oa.isExpelled()).thenReturn(expelled);
            when(_oa.parent()).thenReturn(root ? _soid.oid() : _parent._soid.oid());

            ////////
            // wire services

            when(_ds.hasOA_(eq(_soid))).thenReturn(true);
            when(_ds.getOA_(eq(_soid))).thenReturn(_oa);
            when(_ds.getOANullable_(eq(_soid))).thenReturn(_oa);
            when(_ds.getOAThrows_(eq(_soid))).thenReturn(_oa);
            when(_ds.hasAliasedOA_(eq(_soid))).thenReturn(true);
            when(_ds.getAliasedOANullable_(eq(_soid))).thenReturn(_oa);
            when(_ds.resolve_(_oa)).thenReturn(path);
            when(_ds.resolve_(eq(_soid))).thenReturn(path);
            when(_ds.resolveNullable_(eq(_soid))).thenReturn(path);
            when(_ds.resolveThrows_(eq(_soid))).thenReturn(path);

            when(_ds.getSyncStatus_(eq(_soid))).thenReturn(new BitVector());

            // The path of a root object should be resolved into the anchor's SOID. So we skip
            // mocking the path resolution for roots.
            if (!root) {
                mockPathResolution(path, _soid);
            }
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
            if (_parent == null)
                return new Path();
            Path p = _parent.getPath();
            return _soid.oid().isRoot() ? p : p.append(_name);
        }

        private void mockPathResolution(Path path, @Nullable SOID soid) throws Exception
        {
            if (soid == null) {
                when(_ds.resolveNullable_(argThat(new IsEqualPathIgnoringCase(path))))
                        .thenReturn(null);
                when(_ds.resolveThrows_(argThat(new IsEqualPathIgnoringCase(path))))
                        .thenThrow(new ExNotFound(path.toString()));
            } else {
                when(_ds.resolveNullable_(argThat(new IsEqualPathIgnoringCase(path))))
                        .thenReturn(soid);
                when(_ds.resolveThrows_(argThat(new IsEqualPathIgnoringCase(path))))
                        .thenReturn(soid);
            }
        }

        public void move(MockDSDir newParent, String newName) throws Exception
        {
            assert _parent != null;
            assert newParent != _parent;
            assert newParent != this;

            Path oldPath = getPath();

            _parent.remove(this);
            _parent = newParent;
            boolean expelled = _parent._oa.isExpelled();
            when(_oa.isExpelled()).thenReturn(expelled);
            when(_oa.parent()).thenReturn(_parent.soid().oid());

            if (!_name.equalsIgnoreCase(newName)) {
                _name = newName;
                when(_oa.name()).thenReturn(newName);
            }

            _parent.add(this);

            Path newPath = getPath();
            when(_ds.resolve_(_oa)).thenReturn(newPath);
            when(_ds.resolve_(eq(_soid))).thenReturn(newPath);
            when(_ds.resolveNullable_(eq(_soid))).thenReturn(newPath);
            when(_ds.resolveThrows_(eq(_soid))).thenReturn(newPath);
            updatePathResolution(oldPath, newPath);
        }

        public void updatePathResolution(Path oldPath, Path newPath) throws Exception
        {
            mockPathResolution(oldPath, null);
            mockPathResolution(newPath, _soid);
        }

        public void delete() throws Exception
        {
            // deleting is moving to the trash of the current store
            MockDSDir d = _parent;
            while (d != null && !(d instanceof MockDSAnchor)) {
                d = d._parent;
            }

            move(d != null ? ((MockDSAnchor) d)._thrash : _trash, _soid.oid().toStringFormal());
        }

        public BitVector ss(BitVector newStatus) throws Exception
        {
            BitVector oldStatus = _ds.getSyncStatus_(_soid);
            when(_ds.getSyncStatus_(eq(_soid))).thenReturn(new BitVector(newStatus));
            return oldStatus;
        }
    }

    public class MockDSFile extends MockDSObject
    {
        MockDSFile(String name, MockDSDir parent) throws Exception
        {
            this(name, parent, 1);
        }

        MockDSFile(String name, MockDSDir parent, Integer branches) throws Exception
        {
            super(name, parent);

            when(_oa.type()).thenReturn(Type.FILE);
            when(_oa.isFile()).thenReturn(true);

            final SortedMap<KIndex, CA> cas = Maps.newTreeMap();
            if (_oa.isExpelled() || branches == 0) {
                when(_oa.caMaster()).thenThrow(new AssertionError());
                when(_oa.caMasterNullable()).thenReturn(null);
                when(_oa.caMasterThrows()).thenThrow(new ExNotFound());

                when(_oa.ca(any(KIndex.class))).thenThrow(new AssertionError());
                when(_oa.caNullable(any(KIndex.class))).thenReturn(null);
                when(_oa.caThrows(any(KIndex.class))).thenThrow(new ExNotFound());
            } else {
                // add CAs
                int kMaster = KIndex.MASTER.getInt();
                for (int i = kMaster; i < kMaster + branches; ++i) {
                    CA ca = mock(CA.class);
                    IPhysicalFile pf = mock(IPhysicalFile.class);
                    when(ca.physicalFile()).thenReturn(pf);
                    cas.put(new KIndex(i), ca);
                }

                // special mocking for master
                CA caMaster = cas.get(KIndex.MASTER);
                when(_oa.caMaster()).thenReturn(caMaster);
                when(_oa.caMasterNullable()).thenReturn(caMaster);
                when(_oa.caMasterThrows()).thenReturn(caMaster);

                // generic mocking
                when(_oa.ca(any(KIndex.class))).thenAnswer(new Answer<Object>()
                {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable
                    {
                        KIndex kidx = (KIndex)invocation.getArguments()[0];
                        CA ca = cas.get(kidx);
                        assert ca != null;
                        return ca;
                    }
                });
                when(_oa.caNullable(any(KIndex.class))).thenAnswer(new Answer<Object>()
                {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable
                    {
                        KIndex kidx = (KIndex)invocation.getArguments()[0];
                        return cas.get(kidx);
                    }
                });
                when(_oa.caThrows(any(KIndex.class))).thenAnswer(new Answer<Object>()
                {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable
                    {
                        KIndex kidx = (KIndex)invocation.getArguments()[0];
                        CA ca = cas.get(kidx);
                        if (ca != null) return ca;
                        throw new ExNotFound(_soid + " branch " + kidx);
                    }
                });
            }
            when(_oa.cas()).thenReturn(cas);
            when(_oa.cas(anyBoolean())).thenReturn(cas);
        }

        public MockDSFile ss(boolean... sstat) throws Exception
        {
            when(_ds.getSyncStatus_(eq(_soid))).thenReturn(new BitVector(sstat));
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

        MockDSDir(String name, MockDSDir parent, SOID sidx) throws Exception
        {
            this(name, parent, false, sidx);
        }

        MockDSDir(String name, MockDSDir parent, Boolean expelled, SOID sidx) throws Exception
        {
            super(name, parent, expelled, sidx);

            init();
        }

        private void init() throws Exception
        {
            when(_oa.type()).thenReturn(Type.DIR);
            when(_oa.isDir()).thenReturn(true);
            when(_oa.isDirOrAnchor()).thenReturn(true);

            IPhysicalFolder pf = mock(IPhysicalFolder.class);
            when(_oa.physicalFolder()).thenReturn(pf);

            when(_ds.getAggregateSyncStatus_(eq(_soid))).thenReturn(new CounterVector());

            final MockDSDir _this = this;
            when(_ds.getChild_(eq(_soid.sidx()), eq(_soid.oid()), anyString()))
                    .then(new Answer<OID>()
                    {
                        @Override
                        public OID answer(InvocationOnMock invocation)
                                throws Throwable
                        {
                            Object[] args = invocation.getArguments();
                            String name = (String) args[2];
                            MockDSObject o = _this._children.get(name);
                            return o != null ? o.soid().oid() : null;
                        }
                    });
        }

        @Override
        public void updatePathResolution(Path oldPath, Path newPath) throws Exception
        {
            super.updatePathResolution(oldPath, newPath);

            // recursively update path resolution for children
            for (MockDSObject o : _children.values()) {
                o.updatePathResolution(oldPath.append(o._name), newPath.append(o._name));
            }
        }

        @Override
        public void delete() throws Exception
        {
            // recursively delete children
            for (MockDSObject o : _children.values()) {
                o.delete();
            }

            // undo DS mocking
            when(_ds.getChild_(eq(_soid.sidx()), eq(_soid.oid()), any(String.class)))
                    .thenReturn(null);

            super.delete();
        }

        public MockDSDir ss(boolean... sstat) throws Exception
        {
            when(_ds.getSyncStatus_(eq(_soid))).thenReturn(new BitVector(sstat));
            return this;
        }

        public MockDSDir agss(int... agsstat) throws Exception
        {
            when(_ds.getAggregateSyncStatus_(eq(_soid))).thenReturn(new CounterVector(agsstat));
            return this;
        }

        private <T extends MockDSObject> T child(String name, Class<T> c, Object... param)
                throws Exception
        {
            MockDSObject o = _children.get(name);
            if (o != null) {
                assert o.getClass().equals(c);
                return c.cast(o);
            }
            try {
                Class[] cl = new Class[3 + param.length];
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
            _children.remove(child._name);
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
            Assert.assertTrue(o instanceof  MockDSDir);
            return o instanceof MockDSAnchor ? ((MockDSAnchor)o)._root : (MockDSDir)o;
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

        public MockDSDir dir(String name) throws Exception
        {
            return child(name, MockDSDir.class);
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
        MockDSDir _thrash;

        MockDSAnchor(String name, MockDSDir parent) throws Exception
        {
            this(name, parent, false);
        }

        MockDSAnchor(String name, MockDSDir parent, Boolean expelled) throws Exception
        {
            super(name, parent, expelled);

            // DS mocking
            when(_oa.type()).thenReturn(Type.ANCHOR);
            when(_oa.isDir()).thenReturn(false);
            when(_oa.isAnchor()).thenReturn(true);

            SIndex sidx;
            do {
                sidx = new SIndex(Util.rand().nextInt() % 1000);
            } while (sidx.getInt() < 0 && _sidx2sid != null && _sidx2sid.getNullable_(sidx) != null);

            // SIDMap mocking
            mockSIDMap(SID.anchorOID2storeSID(_oa.soid().oid()), sidx);

            // create root dir
            _root = new MockDSDir(OA.ROOT_DIR_NAME, this, new SOID(sidx, OID.ROOT));
            _thrash = new MockDSDir(C.TRASH, _root, true, new SOID(sidx, OID.TRASH));

            if (!expelled) {
                when(_ds.followAnchorNullable_(_oa)).thenReturn(_root.soid());
                when(_ds.followAnchorThrows_(_oa)).thenReturn(_root.soid());
            } else {
                when(_ds.followAnchorNullable_(_oa)).thenReturn(null);
                when(_ds.followAnchorThrows_(_oa)).thenReturn(null);
            }

            if (_sidx2dbm != null) {
                when(_sidx2dbm.getDeviceMapping_(eq(sidx))).thenReturn(new DeviceBitMap());
            }
        }

        @Override
        public void delete() throws Exception
        {
            // recursively delete children
            _root.delete();
            if (_sidx2dbm != null) {
                when(_sidx2dbm.getDeviceMapping_(eq(_root.soid().sidx()))).thenReturn(new DeviceBitMap());
            }

            when(_ds.followAnchorNullable_(_oa)).thenReturn(null);
            when(_ds.followAnchorThrows_(_oa)).thenReturn(null);

            // undo DS mocking
            super.delete();
        }

        @Override
        public MockDSDir ss(boolean... sstat) throws Exception
        {
            return super.ss(sstat);
        }

        @Override
        public MockDSDir agss(int... agsstat) throws Exception
        {
            _root.agss(agsstat);
            return this;
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
        public MockDSAnchor anchor(String name) throws Exception
        {
            return _root.anchor(name);
        }

        @Override
        public MockDSAnchor anchor(String name, boolean expelled) throws Exception
        {
            return _root.anchor(name, expelled);
        }

        public MockDSAnchor dids(DID... dids) throws Exception
        {
            return dbm(new DeviceBitMap(dids));
        }

        public MockDSAnchor dbm(DeviceBitMap dbm) throws Exception
        {
            assert _sidx2dbm != null;
            when(_sidx2dbm.getDeviceMapping_(eq(_root.soid().sidx()))).thenReturn(dbm);
            return this;
        }
    }

    public MockDS dids(DID... dids) throws Exception
    {
        return dbm(new DeviceBitMap(dids));
    }

    public MockDS dbm(DeviceBitMap dbm) throws Exception
    {
        assert _sidx2dbm != null;
        when(_sidx2dbm.getDeviceMapping_(eq(new SIndex(1)))).thenReturn(dbm);
        return this;
    }

    public SID sid()
    {
        return _sid;
    }

    public MockDSDir root()
    {
        return _root;
    }

    public MockDSDir cd(Path path)
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
        Path path = Path.fromString(p);
        MockDS.MockDSDir d = cd(path.removeLast());
        MockDS.MockDSFile f = d.file(path.last());
        for (IDirectoryServiceListener listener : listeners)
            listener.objectCreated_(f.soid(), d.soid().oid(), path, t);
    }

    public void mkdir(String p, Trans t, IDirectoryServiceListener... listeners) throws Exception
    {
        Path path = Path.fromString(p);
        MockDS.MockDSDir d = cd(path.removeLast());
        MockDS.MockDSDir f = d.dir(path.last());
        for (IDirectoryServiceListener listener : listeners)
            listener.objectCreated_(f.soid(), d.soid().oid(), path, t);
    }

    public void delete(String p, Trans t, IDirectoryServiceListener... listeners) throws Exception
    {
        Path path = Path.fromString(p);
        MockDS.MockDSDir d = cd(path.removeLast());
        MockDS.MockDSObject c = d.child(path.last());
        c.delete();
        for (IDirectoryServiceListener listener : listeners)
            listener.objectDeleted_(c.soid(), d.soid().oid(), path, t);
    }

    public void move(String org, String dst, Trans t, IDirectoryServiceListener... listeners)
            throws Exception
    {
        Path from = Path.fromString(org);
        Path to = Path.fromString(dst);
        MockDS.MockDSDir dfrom = cd(from.removeLast());
        MockDS.MockDSObject c = dfrom.child(from.last());

        MockDS.MockDSDir dto = cd(to.removeLast());
        c.move(dto, to.last());
        for (IDirectoryServiceListener listener : listeners)
            listener.objectMoved_(c.soid(), dfrom.soid().oid(), dto.soid().oid(), from, to, t);
    }

    public void sync(String p, BitVector newStatus, Trans t, IDirectoryServiceListener... listeners)
            throws Exception
    {
        Path path = Path.fromString(p);
        MockDS.MockDSDir d = cd(path.removeLast());
        MockDS.MockDSObject c = d.child(path.last());
        BitVector oldStatus = c.ss(newStatus);
        for (IDirectoryServiceListener listener : listeners)
            listener.objectSyncStatusChanged_(c.soid(), oldStatus, newStatus, t);
    }
}
