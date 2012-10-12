/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.status;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.core.net.IDownloadStateListener.Ended;
import com.aerofs.daemon.core.net.IDownloadStateListener.Ongoing;
import com.aerofs.daemon.core.net.IDownloadStateListener.Started;
import com.aerofs.daemon.core.net.IUploadStateListener.Value;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;
import com.aerofs.testlib.AbstractTest;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static com.aerofs.daemon.core.status.TransferStateAggregator.*;

/**
 *
 */
public class TestTransferStateAggregator extends AbstractTest
{
    // piece of shit @Mock / @InjectMocks are created after the @Before method...
    Endpoint ep = mock(Endpoint.class);
    DirectoryService ds = mock(DirectoryService.class);

    MockDS mds;
    TransferStateAggregator tsa;

    private void startDownload(String path) throws Exception
    {
        SOID soid = ds.resolveThrows_(Path.fromString(path));
        tsa.download_(new SOCID(soid, CID.CONTENT), Started.SINGLETON);
        tsa.download_(new SOCID(soid, CID.CONTENT), new Ongoing(ep, 0, 100));
    }

    private void stopDownload(String path) throws Exception
    {
        SOID soid = ds.resolveThrows_(Path.fromString(path));
        tsa.download_(new SOCID(soid, CID.CONTENT), Ended.SINGLETON_OKAY);
    }

    private void startUpload(String path) throws Exception
    {
        SOID soid = ds.resolveThrows_(Path.fromString(path));
        tsa.upload_(new SOCID(soid, CID.CONTENT), new Value(1, 100));
    }

    private void stopUpload(String path) throws Exception
    {
        SOID soid = ds.resolveThrows_(Path.fromString(path));
        tsa.upload_(new SOCID(soid, CID.CONTENT), new Value(100, 100));
    }

    private void assertStateEquals(int state, String path)
    {
        Assert.assertEquals(state, tsa.state_(Path.fromString(path)));
    }

    @Before
    public void setup() throws Exception
    {
        mds = new MockDS(ds);
        mds.root()
                .dir("foo")
                        .dir("bar")
                                .file("hello").parent()
                                .file("world").parent().parent().parent()
                .dir("baz")
                        .file("stuff").parent().parent()
                .file("bla");

        tsa = new TransferStateAggregator(ds);

        assertStateEquals(NoTransfer, "");
        assertStateEquals(NoTransfer, "foo");
        assertStateEquals(NoTransfer, "foo/bar");
        assertStateEquals(NoTransfer, "foo/bar/hello");
        assertStateEquals(NoTransfer, "foo/bar/world");
        assertStateEquals(NoTransfer, "baz");
        assertStateEquals(NoTransfer, "baz/stuff");
        assertStateEquals(NoTransfer, "bla");
    }

    @After
    public void tearDown() throws Exception
    {
        assertStateEquals(NoTransfer, "");
        assertStateEquals(NoTransfer , "foo");
        assertStateEquals(NoTransfer, "foo/bar");
        assertStateEquals(NoTransfer, "foo/bar/hello");
        assertStateEquals(NoTransfer, "foo/bar/world");
        assertStateEquals(NoTransfer, "baz");
        assertStateEquals(NoTransfer, "baz/stuff");
        assertStateEquals(NoTransfer, "bla");
    }

    @Test
    public void shouldPropagateUpload() throws Exception
    {
        startUpload("foo/bar/hello");

        assertStateEquals(Uploading, "");
        assertStateEquals(Uploading, "foo");
        assertStateEquals(Uploading, "foo/bar");
        assertStateEquals(Uploading, "foo/bar/hello");
        assertStateEquals(NoTransfer, "foo/bar/world");
        assertStateEquals(NoTransfer, "baz");
        assertStateEquals(NoTransfer, "baz/stuff");
        assertStateEquals(NoTransfer, "bla");

        stopUpload("foo/bar/hello");
    }

    @Test
    public void shouldPropagateDownload() throws Exception
    {
        startDownload("foo/bar/world");

        assertStateEquals(Downloading, "");
        assertStateEquals(Downloading, "foo");
        assertStateEquals(Downloading, "foo/bar");
        assertStateEquals(NoTransfer, "foo/bar/hello");
        assertStateEquals(Downloading, "foo/bar/world");
        assertStateEquals(NoTransfer, "baz");
        assertStateEquals(NoTransfer, "baz/stuff");
        assertStateEquals(NoTransfer, "bla");

        stopDownload("foo/bar/world");
    }

    @Test
    public void shouldCombineWhenPropagating() throws Exception
    {
        startDownload("foo/bar/world");
        startUpload("baz/stuff");

        assertStateEquals(Downloading | Uploading, "");
        assertStateEquals(Downloading, "foo");
        assertStateEquals(Downloading, "foo/bar");
        assertStateEquals(NoTransfer, "foo/bar/hello");
        assertStateEquals(Downloading, "foo/bar/world");
        assertStateEquals(Uploading, "baz");
        assertStateEquals(Uploading, "baz/stuff");
        assertStateEquals(NoTransfer, "bla");

        stopUpload("baz/stuff");

        assertStateEquals(Downloading, "");
        assertStateEquals(Downloading , "foo");
        assertStateEquals(Downloading, "foo/bar");
        assertStateEquals(NoTransfer, "foo/bar/hello");
        assertStateEquals(Downloading, "foo/bar/world");
        assertStateEquals(NoTransfer, "baz");
        assertStateEquals(NoTransfer, "baz/stuff");
        assertStateEquals(NoTransfer, "bla");

        stopDownload("foo/bar/world");
    }

    @Test
    public void shouldIgnoreBogusStop() throws Exception
    {
        startDownload("bla");

        assertStateEquals(Downloading, "");
        assertStateEquals(NoTransfer, "foo");
        assertStateEquals(NoTransfer, "foo/bar");
        assertStateEquals(NoTransfer, "foo/bar/hello");
        assertStateEquals(NoTransfer, "foo/bar/world");
        assertStateEquals(NoTransfer, "baz");
        assertStateEquals(NoTransfer, "baz/stuff");
        assertStateEquals(Downloading, "bla");

        stopUpload("foo/bar/world");

        assertStateEquals(Downloading, "");
        assertStateEquals(NoTransfer, "foo");
        assertStateEquals(NoTransfer, "foo/bar");
        assertStateEquals(NoTransfer, "foo/bar/hello");
        assertStateEquals(NoTransfer, "foo/bar/world");
        assertStateEquals(NoTransfer, "baz");
        assertStateEquals(NoTransfer, "baz/stuff");
        assertStateEquals(Downloading, "bla");

        stopDownload("foo/bar/world");

        assertStateEquals(Downloading, "");
        assertStateEquals(NoTransfer, "foo");
        assertStateEquals(NoTransfer, "foo/bar");
        assertStateEquals(NoTransfer, "foo/bar/hello");
        assertStateEquals(NoTransfer, "foo/bar/world");
        assertStateEquals(NoTransfer, "baz");
        assertStateEquals(NoTransfer, "baz/stuff");
        assertStateEquals(Downloading, "bla");

        stopUpload("bla");

        assertStateEquals(Downloading, "");
        assertStateEquals(NoTransfer, "foo");
        assertStateEquals(NoTransfer, "foo/bar");
        assertStateEquals(NoTransfer, "foo/bar/hello");
        assertStateEquals(NoTransfer, "foo/bar/world");
        assertStateEquals(NoTransfer, "baz");
        assertStateEquals(NoTransfer, "baz/stuff");
        assertStateEquals(Downloading, "bla");

        stopDownload("bla");
    }
}
