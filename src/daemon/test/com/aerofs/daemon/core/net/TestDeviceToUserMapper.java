/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.ids.DID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.lib.db.DID2UserDatabase;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.testlib.AbstractTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public final class TestDeviceToUserMapper extends AbstractTest
{
    private final DID _did = new DID(UniqueID.generate());
    private final UserID _userID = UserID.fromInternal("user");
    private final InMemorySQLiteDBCW _dbcw = new InMemorySQLiteDBCW();
    private final TransManager _tm = new TransManager(new Trans.Factory(_dbcw));
    private final DID2UserDatabase _db = Mockito.spy(new DID2UserDatabase(_dbcw.getCoreDBCW()));

    private DeviceToUserMapper _deviceToUserMapper;

    @Before
    public void setup() throws SQLException
    {
        // have to initialize this here so that the
        // when() call above works
        _deviceToUserMapper = new DeviceToUserMapper(mock(TokenManager.class),
                mock(TransportRoutingLayer.class), _db, _tm);

        // initialize the database
        _dbcw.init_();
    }

    @After
    public void tearDown() throws SQLException
    {
        _dbcw.fini_();
    }

    @Test
    public void shouldReturnNullWhenNoMappingExistsForDID()
            throws SQLException
    {
        // make the call
        assertNull(_deviceToUserMapper.getUserIDForDIDNullable_(_did));
    }

    @Test
    public void shouldReturnStoredValueAndRefreshCacheWhenMappingExistsForDID()
            throws SQLException
    {
        // store the mapping
        try (Trans t = _tm.begin_()) {
            _db.insert_(_did, _userID, t);
            t.commit_();
        }

        // verify that we retrieve the correct mapping when asked
        assertEquals(_userID, _deviceToUserMapper.getUserIDForDIDNullable_(_did));

        // make a second call - it should return the correct mapping
        assertEquals(_userID, _deviceToUserMapper.getUserIDForDIDNullable_(_did));

        // check that we only hit the db _once_: the second call should be handled from the cache
        verify(_db, times(1)).getNullable_(_did);
    }

    @Test
    public void shouldStoreDIDToUserIDMappingInDatabaseAndCacheIfNoMappingExists()
            throws SQLException
    {
        // pretend that the UserID was resolved
        _deviceToUserMapper.onUserIDResolved_(_did, _userID);

        // check that we properly stored it
        assertEquals(_userID, _deviceToUserMapper.getUserIDForDIDNullable_(_did));

        // we should have attempted to retrieve the value only once!
        // this call is made just before we make the insert_ call
        // below to verify that the db doesn't already contain the value
        verify(_db, times(1)).getNullable_(_did);

        // we should have stored it in the DB
        verify(_db).insert_(eq(_did), eq(_userID), any(Trans.class));
    }

    @Test
    public void shouldRefreshCacheOnlyIfDIDToUserIDMappingExistsButValueNotInCache()
            throws SQLException
    {
        // store the mapping
        try (Trans t = _tm.begin_()) {
            _db.insert_(_did, _userID, t);
            t.commit_();
        }

        // pretend that the UserID was resolved
        _deviceToUserMapper.onUserIDResolved_(_did, _userID);

        // check that we can retrieve the value
        assertEquals(_userID, _deviceToUserMapper.getUserIDForDIDNullable_(_did));

        // we should have attempted to retrieve the value only once!
        // this call is made just before we make the insert_ call
        // below to verify that the db doesn't already contain the value
        verify(_db, times(1)).getNullable_(_did);

        // this '1' is because of the manual insert
        // if onUserIDResolved_ did this as well, it would have happened twice
        verify(_db, times(1)).insert_(eq(_did), eq(_userID), any(Trans.class));
    }

    @Test
    public void shouldNotThrowExceptionIfDIDToUserIDMappingExistsInDatabaseAndCache()
            throws SQLException
    {
        // store the mapping
        try (Trans t = _tm.begin_()) {
            _db.insert_(_did, _userID, t);
            t.commit_();
        }

        // warm the cache
        assertEquals(_deviceToUserMapper.getUserIDForDIDNullable_(_did), _userID);

        // pretend that we (for some reason) resolve it again
        // this call should not fail because the cache should be warmed
        _deviceToUserMapper.onUserIDResolved_(_did, _userID);
    }

    @Test
    public void shouldRemoveCacheMappingWhenDeviceEvicted()
            throws SQLException
    {
        // pretend that we resolved the UserID
        _deviceToUserMapper.onUserIDResolved_(_did, _userID);

        // evict the device by resolving more devices than the cache can hold
        for (int i = 0; i < DeviceToUserMapper.CACHE_SIZE; ++i) {
            _deviceToUserMapper.onUserIDResolved_(DID.generate(), UserID.DUMMY);
        }

        // attempt to get the userID
        _deviceToUserMapper.getUserIDForDIDNullable_(_did);

        // twice, because we have to hit the DB for the getUserIDForDIDNullable_ call
        verify(_db, times(2)).getNullable_(_did);
    }

    @Test
    public void shouldNotThrowExceptionIfOnUserIDResolvedCalledMultipleTimes()
            throws SQLException
    {
        _deviceToUserMapper.onUserIDResolved_(_did, _userID);
        _deviceToUserMapper.onUserIDResolved_(_did, _userID);
    }
}
