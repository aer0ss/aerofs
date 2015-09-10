/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.daemon.core.lib.BaseStoreJoinerTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;

public class TestMultiuserStoreJoiner extends BaseStoreJoinerTest
{
    @InjectMocks MultiuserStoreJoiner msj;

    @Before
    public void setUp() throws Exception
    {
        isj = msj;
    }

    @Test
    public void joinStore_shouldJoinRootStore() throws Exception
    {
        super.joinStore_shouldJoinRootStore();
    }

    @Test
    public void joinStore_shouldJoinNonRootStore() throws Exception
    {
        super.joinStore_shouldJoinNonRootStore();
    }

    @Test
    public void joinStore_shouldNotJoinOwnRootStore() throws Exception
    {
        super.joinStore_shouldNotJoinOwnRootStore();
    }

    @Test
    public void leaveStore_shouldLeaveRootStore() throws Exception
    {
        super.leaveStore_shouldLeaveRootStore();
    }

    @Test
    public void leaveStore_shouldNotLeaveNonRootStore() throws Exception
    {
        super.leaveStore_shouldNotLeaveNonRootStore();
    }

    @Test
    public void leaveStore_shouldNotLeaveAbsentStore() throws Exception
    {
        super.leaveStore_shouldNotLeaveAbsentStore();
    }
}
