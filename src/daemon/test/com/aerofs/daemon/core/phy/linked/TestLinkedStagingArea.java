/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.phy.linked;

import com.aerofs.ids.SID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.phy.linked.LinkedRevProvider.LinkedRevFile;
import com.aerofs.daemon.core.phy.linked.db.LinkedStagingAreaDatabase;
import com.aerofs.daemon.core.phy.linked.db.LinkedStagingAreaDatabase.StagedFolder;
import com.aerofs.daemon.core.phy.linked.db.LinkedStorageSchema;
import com.aerofs.daemon.core.phy.linked.linker.IgnoreList;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRoot;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRootMap;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.LibParam.AuxFolder;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.testlib.AbstractTest;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.sql.SQLException;

import static com.aerofs.daemon.core.mock.physical.MockPhysicalTree.dir;
import static com.aerofs.daemon.core.mock.physical.MockPhysicalTree.file;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

public class TestLinkedStagingArea extends AbstractTest
{
    @Mock LinkerRootMap lrm;
    @Mock InjectableFile.Factory factFile;
    @Mock CoreScheduler sched;
    @Mock TransManager tm;
    @Mock TokenManager tokenManager;
    @Mock LinkedRevProvider revProvider;

    @Mock Trans t;
    @Mock Token tk;
    @Mock TCB tcb;
    @Mock LinkedRevFile rf;

    InjectableDriver dr = new InjectableDriver(OSUtil.get());
    InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW(dr, mock(CfgUsePolaris.class));
    LinkedStagingAreaDatabase lsadb = new LinkedStagingAreaDatabase(dbcw.getCoreDBCW());

    LinkedStagingArea lsa;

    SID rootSID = SID.generate();

    @Before
    public void setUp() throws Exception
    {
        AppRoot.set("/approot");

        dbcw.init_();
        new LinkedStorageSchema().create_(dbcw.getConnection().createStatement(), dbcw);

        lsa = new LinkedStagingArea(lrm, lsadb, factFile,
                sched, tm, tokenManager,
                mock(IgnoreList.class), revProvider);

        LinkerRoot lr = mock(LinkerRoot.class);
        when(lr.absRootAnchor()).thenReturn("/AeroFS");
        when(lrm.get_(rootSID)).thenReturn(lr);
        when(lrm.absRootAnchor_(rootSID)).thenReturn("/AeroFS");

        when(tm.begin_()).thenReturn(t);
        when(tokenManager.acquire_(any(Cat.class), anyString())).thenReturn(tk);
        when(tk.pseudoPause_(anyString())).thenReturn(tcb);
        when(revProvider.newLocalRevFile(any(Path.class), anyString(), any(KIndex.class)))
                .thenReturn(rf);

        doAnswer(invocation -> {
            ((AbstractEBSelfHandling)invocation.getArguments()[0]).handle_();
            return null;
        }).when(sched).schedule(any(IEvent.class), anyLong());
    }

    @After
    public void tearDown() throws Exception
    {
        dbcw.fini_();
    }

    @Test
    public void shouldStageFolderWithHistoryPath() throws Exception
    {
        shouldStageFolder(Path.fromString(rootSID, "bar"));
    }

    @Test
    public void shouldStageFolderWithEmptyHistoryPath() throws Exception
    {
        shouldStageFolder(Path.root(rootSID));
    }

    private void shouldStageFolder(Path historyPath) throws Exception
    {
        dir("/AeroFS",
                dir("bar",
                        dir("baz"),
                        file("foo"))
        ).mock(factFile, null);

        lsa.stageDeletion_("/AeroFS/bar", historyPath, t);

        verify(factFile.create("/AeroFS/bar"))
                .moveInSameFileSystem(fileAt(auxPath(AuxFolder.STAGING_AREA, "1")));

        IDBIterator<StagedFolder> it = lsadb.listEntries_(0);
        try {
            assertTrue(it.next_());
            StagedFolder sf = it.get_();
            assertEquals(1, sf.id);
            assertEquals(historyPath, sf.historyPath);
            assertFalse(it.next_());
        } finally {
            it.close_();
        }
    }

    private String auxPath(AuxFolder af, String relPath)
    {
        return Util.join(lrm.auxRoot_(rootSID), af._name, relPath);
    }

    static InjectableFile fileAt(final String absPath)
    {
        return argThat(new BaseMatcher<InjectableFile>() {
            @Override
            public boolean matches(Object o)
            {
                return absPath.equals(((InjectableFile)o).getAbsolutePath());
            }

            @Override
            public void describeTo(Description description)
            {
                description.appendValue(absPath);
            }
        });
    }

    InjectableFile staged(Path historyPath) throws Exception
    {
        long id = lsadb.addEntry_(historyPath, t);
        dir(lrm.auxRoot_(rootSID), dir(AuxFolder.STAGING_AREA._name,
                dir(Long.toHexString(id), dir("bar", file("baz")), file("baz"))
        )).mock(factFile, null);

        return factFile.create(auxPath(AuxFolder.STAGING_AREA, Long.toHexString(id)));
    }

    private static Answer<Boolean> DELETE = invocation -> {
        doReturn(false).when((InjectableFile)invocation.getMock()).exists();
        return true;
    };

    @Test
    public void shouldDeleteRecursively() throws Exception
    {
        InjectableFile staged = staged(Path.root(rootSID));
        when(staged.deleteIgnoreError()).thenAnswer(DELETE);

        lsa.start_();

        verify(staged).deleteIgnoreError();
        assertStagingDatabaseEmpty();
    }

    private void assertStagingDatabaseEmpty() throws SQLException
    {
        IDBIterator<StagedFolder> it = lsadb.listEntries_(0);
        try {
            assertFalse(it.next_());
        } finally {
            it.close_();
        }
    }

    @Test
    public void shouldRetryWhenDeleteRecursivelyFails() throws Exception
    {
        InjectableFile staged = staged(Path.root(rootSID));
        when(staged.deleteIgnoreError()).thenReturn(false).thenAnswer(DELETE);

        lsa.start_();

        verify(staged, times(2)).deleteIgnoreError();
        assertStagingDatabaseEmpty();
    }

    @Test
    public void shouldMoveToHistoryRecursively() throws Exception
    {
        InjectableFile staged = staged(Path.fromString(rootSID, "foo"));
        InjectableFile bar = factFile.create(staged, "bar");
        when(bar.deleteIgnoreError()).thenAnswer(DELETE);
        when(staged.deleteIgnoreError()).thenAnswer(DELETE);

        lsa.start_();

        verify(rf, times(2)).save_();
        verify(bar).deleteIgnoreError();
        verify(staged).deleteIgnoreError();
        assertStagingDatabaseEmpty();
    }

    @Test
    public void shouldRetryWhenMoveToHistoryFails() throws Exception
    {
        InjectableFile staged = staged(Path.fromString(rootSID, "foo"));
        InjectableFile bar = factFile.create(staged, "bar");

        doAnswer(new Answer<Void>() {
            int n = 0;
            @Override
            public Void answer(InvocationOnMock invocation) throws IOException
            {
                if ((n++ & 1) == 0) throw new IOException("fail first");
                return null;
            }
        }).when(rf).save_();

        when(bar.deleteIgnoreError()).thenReturn(false).thenAnswer(DELETE);
        when(staged.deleteIgnoreError()).thenReturn(false).thenAnswer(DELETE);

        lsa.start_();

        verify(rf, times(4)).save_();
        verify(bar, times(2)).deleteIgnoreError();
        verify(staged, times(2)).deleteIgnoreError();
        assertStagingDatabaseEmpty();
    }

    @Test
    public void danglingSymlinksShouldActLikeFiles() throws Exception
    {
        InjectableFile staged = staged(Path.fromString(rootSID, "foo"));
        InjectableFile baz = factFile.create(staged, "baz");
        when(baz.deleteIgnoreError()).thenAnswer(DELETE);
        when(staged.deleteIgnoreError()).thenAnswer(DELETE);

        // baz looks like a symlink
        when(baz.isSymbolicLink()).thenReturn(true);
        when(baz.isFile()).thenReturn(false);

        lsa.start_();

        verify(rf, times(2)).save_();
        verify(staged).deleteIgnoreError();
        verify(baz, never()).deleteIgnoreError();
        assertStagingDatabaseEmpty();
    }
}
