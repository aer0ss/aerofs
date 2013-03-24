/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.history;

import com.aerofs.base.id.SID;
import com.aerofs.gui.history.HistoryModel.ModelIndex;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.id.KIndex;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Ritual.GetChildrenAttributesReply;
import com.aerofs.proto.Ritual.ListRevChildrenReply;
import com.aerofs.proto.Ritual.PBBranch;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import com.aerofs.proto.Ritual.PBObjectAttributes.Type;
import com.aerofs.proto.Ritual.PBRevChild;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.Lists;
import junit.framework.Assert;
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

/**
 *
 */
public class TestHistoryModel extends AbstractTest
{
    @Mock CfgLocalUser user;
    @Mock RitualBlockingClient ritual;
    @Mock RitualBlockingClient.Factory factory;

    HistoryModel model;

    @Before
    public void setup() throws Exception
    {
        when(user.get()).thenReturn(UserID.fromInternal("foo@bar.baz"));
        when(factory.create()).thenReturn(ritual);

        model = new HistoryModel(user, factory);
    }

    @After
    public void tearDown() throws Exception
    {
    }

    private void assertIndexList(ModelIndex parent, ModelIndex... indices)
    {
        List<ModelIndex> l = Lists.newArrayList(indices);
        Collections.sort(l);
        int n = model.rowCount(parent);
        for (int i = 0; i < l.size(); ++i) {
            Assert.assertTrue(i < n);
            ModelIndex idx = model.index(parent, i);
            Assert.assertEquals(l.get(i).name, idx.name);
            Assert.assertEquals(l.get(i).isDir, idx.isDir);
            Assert.assertEquals(l.get(i).isDeleted, idx.isDeleted);
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
            return (o instanceof PBPath) ? _p.equals(Path.fromPB((PBPath)o)) : false;
        }
    }

    static PBPath eqPath(Path p) { return argThat(new PathMatcher(p)); }


    @Test
    public void shouldPopulateFirstLevel() throws Exception
    {
        Path root = Path.root(SID.rootSID(user.get()));
        when(ritual.listRevChildren(any(PBPath.class))).thenReturn(ListRevChildrenReply.newBuilder()
                .addChild(PBRevChild.newBuilder().setName("d0").setIsDir(true))
                .addChild(PBRevChild.newBuilder().setName("d1").setIsDir(true))
                .addChild(PBRevChild.newBuilder().setName("foo").setIsDir(false))
                .addChild(PBRevChild.newBuilder().setName("foo").setIsDir(true))
                .addChild(PBRevChild.newBuilder().setName("bar").setIsDir(false))
                .addChild(PBRevChild.newBuilder().setName("baz").setIsDir(false))
                .build());
        when(ritual.getChildrenAttributes(any(String.class), eqPath(root))).thenReturn(
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
        verify(ritual, times(1)).getChildrenAttributes(any(String.class), eqPath(root));

        assertIndexList(null,
                new ModelIndex(null, "f", false, false),
                new ModelIndex(null, "d", true, false),
                new ModelIndex(null, "a", true, false),
                new ModelIndex(null, "d0", true, true),
                new ModelIndex(null, "d0", false, false),
                new ModelIndex(null, "d1", true, false),
                new ModelIndex(null, "foo", false, true),
                new ModelIndex(null, "foo", true, false),
                new ModelIndex(null, "bar", true, false),
                new ModelIndex(null, "bar", false, true),
                new ModelIndex(null, "baz", false, true));
    }

    @Test
    public void shouldNotCrashOnRitualFailure() throws Exception
    {
        when(ritual.listRevChildren(any(PBPath.class))).thenThrow(new Exception());

        model.rowCount(null);

        verify(ritual, times(1)).listRevChildren(any(PBPath.class));
        verify(ritual, never()).getChildrenAttributes(any(String.class), any(PBPath.class));
    }
}
