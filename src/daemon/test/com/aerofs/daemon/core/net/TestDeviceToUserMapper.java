/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.IDeviceEvictionListener;
import com.aerofs.daemon.lib.db.DID2UserDatabase;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class TestDeviceToUserMapper extends AbstractTest
{
    InMemorySQLiteDBCW _dbcw = new InMemorySQLiteDBCW();
    DID2UserDatabase _db = Mockito.spy(new DID2UserDatabase(_dbcw.getCoreDBCW()));
    DeviceToUserMapper _deviceToUserMapper;

    DID _did = new DID(UniqueID.generate());
    AtomicReference<IDeviceEvictionListener> _evictionListener = new AtomicReference<IDeviceEvictionListener>(null);

    @Before
    public void setup()
            throws SQLException
    {
        CoreDeviceLRU deviceLRU = Mockito.mock(CoreDeviceLRU.class);
        Mockito.doAnswer(new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                _evictionListener.set((IDeviceEvictionListener) invocation.getArguments()[0]);
                return null;
            }
        }).when(deviceLRU).addEvictionListener_(Mockito.any(IDeviceEvictionListener.class));

        _dbcw.init_();
        _deviceToUserMapper = new DeviceToUserMapper(null, null, _db, deviceLRU, null);
    }

    @Test
    public void shouldReturnNullForNonExistentDID()
            throws SQLException
    {
        assertEquals(_deviceToUserMapper.getUserIDForDIDNullable_(_did), null);
    }

    @Test
    public void shouldReturnAddedMapping()
            throws SQLException
    {
        UserID userId = UserID.fromInternal("hohoho");
        _deviceToUserMapper.onUserIDResolved_(_did, userId, null);
        assertEquals(_deviceToUserMapper.getUserIDForDIDNullable_(_did), userId);
        Mockito.verify(_db, Mockito.times(1)).getNullable_(_did); // only once to check if the userID was already added; second time it should come from the cache
    }

    @Test
    public void shouldRemoveCacheMappingWhenDeviceEvicted()
            throws SQLException
    {
        UserID userId = UserID.fromInternal("hohoho");

        // pretend that we resolved the UserID
        _deviceToUserMapper.onUserIDResolved_(_did, userId, null);

        // now, evict the device
        _evictionListener.get().evicted_(_did);

        // attempt to get the userID
        _deviceToUserMapper.getUserIDForDIDNullable_(_did);

        Mockito.verify(_db, Mockito.times(2)).getNullable_(_did); // twice, because we have to hit the DB for the getUserIDForDIDNullable_ call
    }

    @Test
    public void shouldNotFailOnDuplicateAdditions()
            throws SQLException
    {
        _deviceToUserMapper.onUserIDResolved_(_did, UserID.fromInternal("hohoho"), null);
        _deviceToUserMapper.onUserIDResolved_(_did, UserID.fromInternal("hohoho"), null);
    }
}
