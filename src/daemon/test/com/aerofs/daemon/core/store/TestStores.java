package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.id.SIndex;
import com.aerofs.testlib.AbstractTest;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.sql.SQLException;

public class TestStores extends AbstractTest
{
    @Mock IStoreDatabase sdb;
    @Mock TransManager tm;
    @Mock StoreCreator sc;
    @Mock SIDMap sm;
    @Mock MapSIndex2Store sidx2s;
    @Mock DevicePresence dp;

    @Mock Trans t;

    @InjectMocks Stores ss;

    SIndex sidx = new SIndex(1);
    SIndex sidxParent = new SIndex(2);

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnAddingExisingStore() throws SQLException
    {
        ss.add_(sidx, sidxParent, t);
        ss.add_(sidx, sidxParent, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnDeletingNonexisingStore() throws SQLException
    {
        ss.delete_(sidx, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnSettingParentOnNonexisingStore() throws SQLException
    {
        ss.setParent_(sidx, sidxParent, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnGettingParentOnNonexisingStore() throws SQLException
    {
        ss.getParent_(sidx);
    }

    @Test(expected = AssertionError.class)
    public void shouldFailAssertionOnGettingChildrenOnNonexisingStore() throws SQLException
    {
        ss.getChildren_(sidx);
    }
}
