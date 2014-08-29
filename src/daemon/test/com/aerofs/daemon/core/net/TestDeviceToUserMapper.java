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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public final class TestDeviceToUserMapper extends AbstractTest
{
    private final DID _did = new DID(UniqueID.generate());
    private final UserID _userID = UserID.fromInternal("user");
    private final InMemorySQLiteDBCW _dbcw = new InMemorySQLiteDBCW();
    private final DID2UserDatabase _db = Mockito.spy(new DID2UserDatabase(_dbcw.getCoreDBCW()));
    private final CoreDeviceLRU _deviceLRU = Mockito.mock(CoreDeviceLRU.class);
    private final AtomicReference<IDeviceEvictionListener> _evictionListener = new AtomicReference<>(null);

    private DeviceToUserMapper _deviceToUserMapper;

    @Before
    public void setup()
            throws SQLException
    {
        // store the eviction listener
        Mockito.doAnswer(invocation -> {
            _evictionListener.set((IDeviceEvictionListener) invocation.getArguments()[0]);
            return null;
        }).when(_deviceLRU).addEvictionListener_(Mockito.any(IDeviceEvictionListener.class));

        // have to initialize this here so that the
        // when() call above works
        _deviceToUserMapper = new DeviceToUserMapper(null, null, _db, _deviceLRU, null); // SUT

        // initialize the database
        _dbcw.init_();
    }

    @Test
    public void shouldReturnNullWhenNoMappingExistsForDID()
            throws SQLException
    {
        // make the call
        assertEquals(_deviceToUserMapper.getUserIDForDIDNullable_(_did), null);

        // regardless of the result, we should refresh the device cache
        verify(_deviceLRU).addDevice_(_did);
    }

    @Test
    public void shouldReturnStoredValueAndRefreshCacheWhenMappingExistsForDID()
            throws SQLException
    {
        // store the mapping
        _db.insert_(_did, _userID, null);

        // verify that we retrieve the correct mapping when asked
        assertEquals(_userID, _deviceToUserMapper.getUserIDForDIDNullable_(_did));

        // make a second call - it should return the correct mapping
        assertEquals(_userID, _deviceToUserMapper.getUserIDForDIDNullable_(_did));

        // check that we only hit the db _once_: the second call should be handled from the cache
        verify(_db, times(1)).getNullable_(_did);

        // we should have refreshed the device cache twice
        verify(_deviceLRU, times(2)).addDevice_(_did);
    }

    @Test
    public void shouldStoreDIDToUserIDMappingInDatabaseAndCacheIfNoMappingExists()
            throws SQLException
    {
        // pretend that the UserID was resolved
        _deviceToUserMapper.onUserIDResolved_(_did, _userID, null);

        // check that we properly stored it
        assertEquals(_userID, _deviceToUserMapper.getUserIDForDIDNullable_(_did));

        // we should have attempted to retrieve the value only once!
        // this call is made just before we make the insert_ call
        // below to verify that the db doesn't already contain the value
        verify(_db, times(1)).getNullable_(_did);

        // we should have stored it in the DB
        verify(_db).insert_(_did, _userID, null);

        // we should have refreshed the device cache twice
        // 1st time: onUserIDResolved_
        // 2nd time: getUserIDForDIDNullable_
        verify(_deviceLRU, times(2)).addDevice_(_did);
    }

    @Test
    public void shouldRefreshCacheOnlyIfDIDToUserIDMappingExistsButValueNotInCache()
            throws SQLException
    {
        // store the mapping
        _db.insert_(_did, _userID, null);

        // pretend that the UserID was resolved
        _deviceToUserMapper.onUserIDResolved_(_did, _userID, null);

        // check that we can retrieve the value
        assertEquals(_userID, _deviceToUserMapper.getUserIDForDIDNullable_(_did));

        // we should have attempted to retrieve the value only once!
        // this call is made just before we make the insert_ call
        // below to verify that the db doesn't already contain the value
        verify(_db, times(1)).getNullable_(_did);

        // this '1' is because of the manual insert
        // if onUserIDResolved_ did this as well, it would have happened twice
        verify(_db, times(1)).insert_(_did, _userID, null);

        // we should have refreshed the device cache twice
        // 1st time: onUserIDResolved_
        // 2nd time: getUserIDForDIDNullable_
        verify(_deviceLRU, times(2)).addDevice_(_did);
    }

    @Test
    public void shouldNotThrowExceptionIfDIDToUserIDMappingExistsInDatabaseAndCache()
            throws SQLException
    {
        // store the mapping
        _db.insert_(_did, _userID, null);

        // warm the cache
        assertEquals(_deviceToUserMapper.getUserIDForDIDNullable_(_did), _userID);

        // pretend that we (for some reason) resolve it again
        // this call should not fail because the cache should be warmed
        _deviceToUserMapper.onUserIDResolved_(_did, _userID, null);

        // we should have refreshed the device cache twice
        // 1st time: getUserIDForDIDNullable_
        // 2nd time: onUserIDResolved_
        verify(_deviceLRU, times(2)).addDevice_(_did);
    }

    @Test
    public void shouldRemoveCacheMappingWhenDeviceEvicted()
            throws SQLException
    {
        // pretend that we resolved the UserID
        _deviceToUserMapper.onUserIDResolved_(_did, _userID, null);

        // now, evict the device
        _evictionListener.get().evicted_(_did);

        // attempt to get the userID
        _deviceToUserMapper.getUserIDForDIDNullable_(_did);

        // twice, because we have to hit the DB for the getUserIDForDIDNullable_ call
        verify(_db, times(2)).getNullable_(_did);
    }

    @Test
    public void shouldNotThrowExceptionIfOnUserIDResolvedCalledMultipleTimes()
            throws SQLException
    {
        _deviceToUserMapper.onUserIDResolved_(_did, _userID, null);
        _deviceToUserMapper.onUserIDResolved_(_did, _userID, null);
    }
}
