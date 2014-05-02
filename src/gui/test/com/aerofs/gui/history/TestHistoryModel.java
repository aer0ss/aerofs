/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.history;

import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.gui.history.HistoryModel.ModelIndex;
import com.aerofs.lib.Path;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.id.KIndex;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Ritual.GetChildrenAttributesReply;
import com.aerofs.proto.Ritual.ListRevChildrenReply;
import com.aerofs.proto.Ritual.PBBranch;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import com.aerofs.proto.Ritual.PBObjectAttributes.Type;
import com.aerofs.proto.Ritual.PBRevChild;
import com.aerofs.ritual.IRitualClientProvider;
import com.aerofs.ritual.RitualBlockingClient;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;

import java.util.Collections;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestHistoryModel extends AbstractTest
{
    @Mock CfgLocalUser cfgLocalUser;
    @Mock IRitualClientProvider ritualProvider;
    @Mock RitualBlockingClient ritual;
    @Mock CfgAbsRoots cfgAbsRoots;

    HistoryModel model;

    private final UserID user = UserID.fromInternal("foo@bar.baz");
    private final SID rootSID = SID.rootSID(user);

    @Before
    public void setup() throws Exception
    {
        when(cfgLocalUser.get()).thenReturn(user);
        when(ritualProvider.getBlockingClient()).thenReturn(ritual);
        when(cfgAbsRoots.getAll()).thenReturn(ImmutableMap.of(rootSID, "/AeroFS"));
        when(cfgAbsRoots.getNullable(rootSID)).thenReturn("/AeroFS");

        model = new HistoryModel(ritualProvider, StorageType.LINKED, cfgAbsRoots, cfgLocalUser);
    }

    @After
    public void tearDown() throws Exception
    {
    }

    private void assertIndexList(ModelIndex parent, ModelIndex... indices)
    {
        List<ModelIndex> list = Lists.newArrayList(indices);
        Collections.sort(list);
        int n = model.rowCount(parent);
        for (int i = 0; i < list.size(); ++i) {
            Assert.assertTrue(i < n);
            ModelIndex idx = model.index(parent, i);
            Assert.assertEquals(list.get(i).name(), idx.name());
            Assert.assertEquals(list.get(i).isDir, idx.isDir);
            Assert.assertEquals(list.get(i).isDeleted, idx.isDeleted);
        }
    }

    static class PathMatcher extends ArgumentMatcher<PBPath>
    {
        private final Path _p;

        public PathMatcher(Path p)
        {
            _p = p;
        }

        @Override
        public boolean matches(Object o)
        {
            return o instanceof PBPath && _p.equals(Path.fromPB((PBPath) o));
        }
    }

    static PBPath eqPath(Path p) { return argThat(new PathMatcher(p)); }

    @Test
    public void shouldPopulateFirstLevelWithFirstLevelOfRootSID() throws Exception
    {
        Path root = Path.root(rootSID);
        when(ritual.listRevChildren(any(PBPath.class))).thenReturn(ListRevChildrenReply.newBuilder()
                .addChild(PBRevChild.newBuilder().setName("d0").setIsDir(true))
                .addChild(PBRevChild.newBuilder().setName("d1").setIsDir(true))
                .addChild(PBRevChild.newBuilder().setName("foo").setIsDir(false))
                .addChild(PBRevChild.newBuilder().setName("foo").setIsDir(true))
                .addChild(PBRevChild.newBuilder().setName("bar").setIsDir(false))
                .addChild(PBRevChild.newBuilder().setName("baz").setIsDir(false))
                .build());
        when(ritual.getChildrenAttributes(eqPath(root))).thenReturn(
                GetChildrenAttributesReply.newBuilder()
                        .addChildrenName("f-expelled")
                        .addChildrenAttributes(PBObjectAttributes.newBuilder()
                                .setType(Type.FILE)
                                .setExcluded(true)
                                .addBranch(PBBranch.newBuilder()
                                        .setKidx(KIndex.MASTER.getInt())
                                        .setMtime(0)
                                        .setLength(0)))
                        .addChildrenName("d-expelled")
                        .addChildrenAttributes(PBObjectAttributes.newBuilder()
                                .setType(Type.FOLDER)
                                .setExcluded(true))
                        .addChildrenName("a-expelled")
                        .addChildrenAttributes(PBObjectAttributes.newBuilder()
                                .setType(Type.SHARED_FOLDER)
                                .setExcluded(true))
                        .addChildrenName("f")
                        .addChildrenAttributes(PBObjectAttributes.newBuilder()
                                .setType(Type.FILE)
                                .setExcluded(false)
                                .addBranch(PBBranch.newBuilder()
                                        .setKidx(KIndex.MASTER.getInt())
                                        .setMtime(0)
                                        .setLength(0)))
                        .addChildrenName("d")
                        .addChildrenAttributes(PBObjectAttributes.newBuilder()
                                .setType(Type.FOLDER)
                                .setExcluded(false))
                        .addChildrenName("a")
                        .addChildrenAttributes(PBObjectAttributes.newBuilder()
                                .setType(Type.SHARED_FOLDER)
                                .setExcluded(false))
                        .addChildrenName("d0")
                        .addChildrenAttributes(PBObjectAttributes.newBuilder()
                                .setType(Type.FILE)
                                .setExcluded(false)
                                .addBranch(PBBranch.newBuilder()
                                        .setKidx(KIndex.MASTER.getInt())
                                        .setMtime(0)
                                        .setLength(0)))
                        .addChildrenName("d1")
                        .addChildrenAttributes(PBObjectAttributes.newBuilder()
                                .setType(Type.FOLDER)
                                .setExcluded(false))
                        .addChildrenName("foo")
                        .addChildrenAttributes(PBObjectAttributes.newBuilder()
                                .setType(Type.SHARED_FOLDER)
                                .setExcluded(false))
                        .addChildrenName("bar")
                        .addChildrenAttributes(PBObjectAttributes.newBuilder()
                                .setType(Type.FOLDER)
                                .setExcluded(false))
                        .build());

        Assert.assertEquals(11, model.rowCount(null));

        verify(ritual, times(1)).listRevChildren(eqPath(root));
        verify(ritual, times(1)).getChildrenAttributes(eqPath(root));

        assertIndexList(null,
                new ModelIndex(model, Path.fromString(rootSID, "f"), false, false),
                new ModelIndex(model, Path.fromString(rootSID, "d"), true, false),
                new ModelIndex(model, Path.fromString(rootSID, "a"), true, false),
                new ModelIndex(model, Path.fromString(rootSID, "d0"), true, true),
                new ModelIndex(model, Path.fromString(rootSID, "d0"), false, false),
                new ModelIndex(model, Path.fromString(rootSID, "d1"), true, false),
                new ModelIndex(model, Path.fromString(rootSID, "foo"), false, true),
                new ModelIndex(model, Path.fromString(rootSID, "foo"), true, false),
                new ModelIndex(model, Path.fromString(rootSID, "bar"), true, false),
                new ModelIndex(model, Path.fromString(rootSID, "bar"), false, true),
                new ModelIndex(model, Path.fromString(rootSID, "baz"), false, true));
    }

    @Test
    public void shouldPopulateFirstLevelWithPhysicalRoots() throws Exception
    {
        SID ext = SID.generate();
        when(cfgAbsRoots.getAll()).thenReturn(ImmutableMap.of(rootSID, "/AeroFS", ext, "/ext"));
        when(cfgAbsRoots.getNullable(ext)).thenReturn("/ext");

        assertIndexList(null,
                new ModelIndex(model, Path.root(rootSID), true, false),
                new ModelIndex(model, Path.root(ext), true, false));
    }

    @Test
    public void shouldNotCrashOnRitualFailure() throws Exception
    {
        when(ritual.listRevChildren(any(PBPath.class))).thenThrow(new Exception());

        model.rowCount(null);

        verify(ritual, times(1)).listRevChildren(any(PBPath.class));
        verify(ritual, never()).getChildrenAttributes(any(PBPath.class));
    }

    @Test
    public void shouldPopulateTheDefaultRootForNonLinkedStorage()
            throws Exception
    {
        when(cfgLocalUser.get()).thenReturn(user);

        Path root = Path.root(rootSID);
        when(ritual.listRevChildren(any(PBPath.class))).thenReturn(ListRevChildrenReply.newBuilder()
                .addChild(PBRevChild.newBuilder().setName("a").setIsDir(true))
                .build());
        when(ritual.getChildrenAttributes(eqPath(root))).thenReturn(
                GetChildrenAttributesReply.newBuilder()
                        .addChildrenName("a")
                        .addChildrenAttributes(PBObjectAttributes.newBuilder()
                                .setType(Type.SHARED_FOLDER)
                                .setExcluded(false))
                        .build());

        // Create a model with non-linked storage type
        model = new HistoryModel(ritualProvider, StorageType.LOCAL, cfgAbsRoots, cfgLocalUser);

        assertIndexList(null,
                new ModelIndex(model, Path.fromString(rootSID, "a"), true, false));
    }
}
