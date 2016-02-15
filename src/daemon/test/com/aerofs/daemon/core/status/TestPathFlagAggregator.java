/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.status;

import com.aerofs.ids.SID;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferProgress;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ClientParam;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.CID;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;
import com.aerofs.ids.UniqueID;
import com.aerofs.testlib.AbstractTest;
import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static com.aerofs.daemon.core.status.PathFlagAggregator.*;

@RunWith(Parameterized.class)
public class TestPathFlagAggregator extends AbstractTest
{
    // parametrize test with default transfer direction
    @Parameters public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {Uploading}, {Downloading}
        });
    }

    private final int direction;

    private static int opposite(int direction) {
        switch (direction) {
        case Uploading: return Downloading;
        case Downloading: return Uploading;
        default: throw new IllegalArgumentException();
        }
    }

    public TestPathFlagAggregator(int direction)
    {
        this.direction = direction;
    }

    @Mock Trans t;
    @Mock Endpoint ep;

    @InjectMocks PathFlagAggregator tsa;

    SID rootSID = SID.generate();

    SOID createSOID() throws Exception
    {
        return new SOID(new SIndex(1), new OID(UniqueID.generate()));
    }

    void simulateTransferStart(int d, SOID soid, String p) throws Exception
    {
        assert d == Downloading || d == Uploading;

        Path path = Path.fromString(rootSID, p);
        SOCID socid = new SOCID(soid, CID.CONTENT);

        tsa.changeFlagsOnTransferNotification_(socid, path, new TransferProgress(1, 100), d);
    }

    void simulateTransferProgress(int d, SOID soid, String p, int percent) throws Exception
    {
        assert d == Downloading || d == Uploading;
        assert percent > 0 && percent < 100;

        Path path = Path.fromString(rootSID, p);
        SOCID socid = new SOCID(soid, CID.CONTENT);

        tsa.changeFlagsOnTransferNotification_(socid, path, new TransferProgress(percent, 100), d);
    }

    void simulateTransferEnd(int d, SOID soid, @Nullable String p) throws Exception
    {
        assert d == Downloading || d == Uploading;

        Path path = p == null ? null : Path.fromString(rootSID, p);
        SOCID socid = new SOCID(soid, CID.CONTENT);

        tsa.changeFlagsOnTransferNotification_(socid, path, new TransferProgress(100, 100), d);
    }

    private void assertStateEquals(int state, String... pathList)
    {
        for (String path : pathList) {
            Assert.assertEquals(state, tsa.state_(Path.fromString(rootSID, path)));
        }
    }

    private void assertNoOngoingTransfers() throws Exception
    {
        Assert.assertTrue(!tsa.hasOngoingTransfers_());
    }

    @Before
    public void setup() throws Exception
    {
        assertNoOngoingTransfers();
    }

    @After
    public void tearDown() throws Exception
    {
        assertNoOngoingTransfers();
    }

    @Test
    public void shouldPropagateTransfer() throws Exception
    {
        SOID o1 = createSOID();  // foo/bar/hello
        simulateTransferStart(direction, o1, "foo/bar/hello");
        assertStateEquals(direction, "", "foo", "foo/bar", "foo/bar/hello");

        simulateTransferEnd(direction, o1, "foo/bar/hello");
    }

    @Test
    public void shouldCombineTransferStatusWhenPropagating() throws Exception
    {
        SOID o1 = createSOID();  // foo/bar/world
        SOID o2 = createSOID();  // baz/stuff
        simulateTransferStart(direction, o1, "foo/bar/world");
        simulateTransferStart(opposite(direction), o2, "baz/stuff");

        assertStateEquals(Downloading | Uploading, "");
        assertStateEquals(direction, "foo", "foo/bar", "foo/bar/world");
        assertStateEquals(opposite(direction), "baz", "baz/stuff");

        simulateTransferEnd(opposite(direction), o2, "baz/stuff");
        assertStateEquals(direction, "", "foo", "foo/bar", "foo/bar/world");

        simulateTransferEnd(direction, o1, "foo/bar/world");
    }

    @Test
    public void shouldIgnoreBogusTransferEnd() throws Exception
    {
        SOID o1 = createSOID();  // bla
        SOID o2 = createSOID();  // foo/bar/world
        simulateTransferStart(direction, o1, "bla");
        assertStateEquals(direction, "", "bla");

        simulateTransferEnd(opposite(direction), o2, "foo/bar/world");
        assertStateEquals(direction, "", "bla");

        simulateTransferEnd(direction, o2, "foo/bar/world");
        assertStateEquals(direction, "", "bla");

        simulateTransferEnd(opposite(direction), o1, "bla");
        assertStateEquals(direction, "", "bla");

        simulateTransferEnd(direction, o1, "bla");
    }

    @Test
    public void shouldHandleRenameDuringTransfer() throws Exception
    {
        SOID o1 = createSOID();  // foo/bar/hello
        simulateTransferStart(direction, o1, "foo/bar/hello");
        assertStateEquals(direction, "", "foo", "foo/bar", "foo/bar/hello");

        simulateTransferProgress(direction, o1, "baz/hello", 50);
        assertStateEquals(direction, "", "baz", "baz/hello");

        simulateTransferEnd(direction, o1, "baz/hello");
    }

    @Test
    public void shouldHandleDeletionDuringTransfer() throws Exception
    {
        SOID o1 = createSOID();  // foo/bar/hello
        simulateTransferStart(direction, o1, "foo/bar/hello");
        assertStateEquals(direction, "", "foo", "foo/bar", "foo/bar/hello");

        simulateTransferEnd(direction, o1, Util.join(ClientParam.TRASH, "deadbeef"));
    }

    /**
     * OID can only deleted as a result of:
     *   - aliasing
     *   - expulsion of an entire store (the whole store is deleted and the DB is cleaned of all
     *   references to it)
     */
    @Test
    public void shouldHandleOIDDeletionDuringTransfer() throws Exception
    {
        SOID o1 = createSOID();  // foo/bar/hello
        simulateTransferStart(direction, o1, "foo/bar/hello");
        assertStateEquals(direction, "", "foo", "foo/bar", "foo/bar/hello");

        simulateTransferEnd(direction, o1, null);
    }
}
