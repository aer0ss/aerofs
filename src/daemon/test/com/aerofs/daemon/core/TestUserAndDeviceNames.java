/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core;

import com.aerofs.base.C;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.id.DID;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(UserAndDeviceNames.class)
public class TestUserAndDeviceNames extends AbstractTest
{
    @Mock UserAndDeviceNames _udn;

    @Before
    public void setup() throws Exception
    {
        // mock static so we have control on time
        PowerMockito.mockStatic(System.class);

        // mock udn to call the real updateLocalDeviceInfo_ and setSPLoginDelay
        when(_udn.updateLocalDeviceInfo_(anyListOf(DID.class))).thenCallRealMethod();
        doCallRealMethod().when(_udn).setSPLoginDelay(anyLong());

        // mock udn to throw bad credential whenever the implementation is called
        doThrow(new ExBadCredential()).when(_udn).updateLocalDeviceInfoImpl_(anyListOf(DID.class));

        _udn.setSPLoginDelay(30 * C.MIN);
    }

    @Test
    public void shouldThrottleToOnce() throws Exception
    {
        // the numbers have been cooked so impl will only be invoked once
        configureTimes(1 * C.SEC, 2 * C.SEC, 3 * C.SEC, 4 * C.SEC, 5 * C.SEC);
        invokeManyTimes(5);
        verify(_udn, times(1)).updateLocalDeviceInfoImpl_(anyListOf(DID.class));
    }

    @Test
    public void shouldThrottleToTwice() throws Exception
    {
        // the numbers have been cooked so impl will be invoked exactly twice
        configureTimes(1 * C.MIN, 10 * C.MIN, 20 * C.MIN, 30 * C.MIN, 40 * C.MIN, 50 * C.MIN);
        invokeManyTimes(6);
        verify(_udn, times(2)).updateLocalDeviceInfoImpl_(anyListOf(DID.class));
    }

    @Test
    public void shouldNotThrottle() throws Exception
    {
        // the numbers have been cooked so impl will be invoked every time
        configureTimes(100 * C.MIN, 200 * C.MIN, 300 * C.MIN, 400 * C.MIN, 500 * C.MIN);
        invokeManyTimes(5);
        verify(_udn, times(5)).updateLocalDeviceInfoImpl_(anyListOf(DID.class));
    }

    private void configureTimes(Long time0, Long... times)
    {
        when(System.currentTimeMillis()).thenReturn(time0, times);
    }

    private void invokeManyTimes(int times) throws Exception
    {
        List<DID> list = Collections.<DID>emptyList();
        for (int i = 0; i < times; i++) {
            _udn.updateLocalDeviceInfo_(list);
        }
    }
}
