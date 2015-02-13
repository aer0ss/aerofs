/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.quota;

import com.aerofs.ids.OID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.IDirectoryServiceListener;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOKID;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestStoreUsageCache extends AbstractTest
{
    @Mock DirectoryService ds;

    @InjectMocks StoreUsageCache suc;

    @Captor ArgumentCaptor<IDirectoryServiceListener> captorDSListener;

    SIndex sidx = new SIndex(123);

    // the listener the StoreUsageCache has registered with the DS.
    IDirectoryServiceListener listener;

    @Before
    public void setUp()
            throws SQLException
    {
        verify(ds).addListener_(captorDSListener.capture());
        listener = captorDSListener.getValue();
    }

    private void mockDSGetBytesUsed(long usage)
            throws SQLException
    {
        when(ds.getBytesUsed_(any(SIndex.class))).thenReturn(usage);
    }

    @Test
    public void shouldCacheValues()
            throws SQLException
    {
        mockDSGetBytesUsed(123);
        assertEquals(suc.getBytesUsed_(sidx), 123);
        mockDSGetBytesUsed(456);
        assertEquals(suc.getBytesUsed_(sidx), 123);
    }

    @Test
    public void shouldInvalidateCacheOnContentChanges()
            throws SQLException
    {
        mockDSGetBytesUsed(123);
        assertEquals(suc.getBytesUsed_(sidx), 123);

        listener.objectContentCreated_(new SOKID(sidx, OID.generate(), KIndex.MASTER), null, null);
        mockDSGetBytesUsed(456);
        assertEquals(suc.getBytesUsed_(sidx), 456);

        listener.objectContentDeleted_(new SOKID(sidx, OID.generate(), KIndex.MASTER), null);
        mockDSGetBytesUsed(789);
        assertEquals(suc.getBytesUsed_(sidx), 789);

        listener.objectContentModified_(new SOKID(sidx, OID.generate(), KIndex.MASTER), null, null);
        mockDSGetBytesUsed(100);
        assertEquals(suc.getBytesUsed_(sidx), 100);

    }
}
