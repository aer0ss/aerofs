package com.aerofs.devman.server.db;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.DID;
import com.aerofs.devman.server.db.LastSeenDatabase.LastSeenTime;
import com.aerofs.servlets.lib.db.AbstractJedisTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(LastSeenDatabase.class)
public class TestLastSeenDatabase extends AbstractJedisTest
{
    private final LastSeenDatabase _lsd = new LastSeenDatabase((getTransaction()));
    private final DID _d = DID.generate();

    private final long MOCK_TIME = 11142L;

    @Before
    public void setupTestLastSeenDatabase()
    {
        // Mock the system time static method.
        PowerMockito.mockStatic(System.class);
        Mockito.when(System.currentTimeMillis()).thenReturn(MOCK_TIME);
    }

    @Test
    public void testSetDeviceTime()
            throws ExNotFound
    {
        getTransaction().begin();
        _lsd.setDeviceSeenNow(_d);
        LastSeenTime lst = _lsd.getLastSeenTime(_d);
        getTransaction().commit();

        Assert.assertTrue(lst.exists());
        Assert.assertEquals(MOCK_TIME, lst.get());
    }

    @Test (expected = ExNotFound.class)
    public void testDeviceNotFound()
            throws ExNotFound
    {
        getTransaction().begin();
        LastSeenTime lst = _lsd.getLastSeenTime(DID.generate());
        getTransaction().commit();

        Assert.assertFalse(lst.exists());

        // Expect this to throw.
        lst.get();
    }
}