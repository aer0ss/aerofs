/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core;

import com.aerofs.base.C;
import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.net.DeviceToUserMapper;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.IUserAndDeviceNameDatabase;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(ElapsedTimer.class)
@PowerMockIgnore({"ch.qos.logback.*", "org.slf4j.*"})
public class TestUserAndDeviceNames extends AbstractTest
{
    @Mock CfgLocalUser user;
    @Mock TokenManager tokenManager;
    @Mock TransManager tm;
    @Mock DeviceToUserMapper d2u;
    @Mock IUserAndDeviceNameDatabase udndb;
    @Mock InjectableSPBlockingClientFactory factSP;
    @Spy UserAndDeviceNames _udn = new UserAndDeviceNames(user, tokenManager, tm, d2u, udndb, factSP);
    @Mock Token tk;
    @Mock SPBlockingClient spClient;

    @Before
    public void setup() throws Exception
    {
        // mock static so we have control on time
        PowerMockito.mockStatic(System.class);

        // TODO (DF): replace mocking of System by making an ElapsedTimer factory and injecting it
        //            then use a mock ElapsedTimer that gives the desired responses to elapsed()
        //            that way we no longer depend on how many times nanoTime() gets called (ewww)

        when(user.get()).thenReturn(UserID.fromInternal("test@aerofs.com"));
        when(tokenManager.acquireThrows_(any(Cat.class), any(String.class))).thenReturn(tk);
        doThrow(new ExBadCredential()).when(spClient).signInRemote();
        when(factSP.create()).thenReturn(spClient);

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
        configureTimes(100 * C.MIN, // start
                200 * C.MIN, 200 * C.MIN + 2, // elapsed(), restart()
                300 * C.MIN, 300 * C.MIN + 2, // elapsed(), restart()
                400 * C.MIN, 400 * C.MIN + 2, // elapsed(), restart()
                500 * C.MIN, 500 * C.MIN + 2); // elapsed(), restart()
        invokeManyTimes(5);
        verify(_udn, times(5)).updateLocalDeviceInfoImpl_(anyListOf(DID.class));
    }

    private void configureTimes(Long time0, Long... times)
    {
        Long[] nanotimes = new Long[times.length];
        for (int i = 0; i < times.length; i++) {
            nanotimes[i] = times[i] * C.NSEC_PER_MSEC;
        }
        when(System.nanoTime()).thenReturn(time0 * C.NSEC_PER_MSEC, nanotimes);
    }

    private void invokeManyTimes(int times) throws Exception
    {
        List<DID> list = Collections.<DID>emptyList();
        for (int i = 0; i < times; i++) {
            _udn.updateLocalDeviceInfo_(list);
        }
    }
}
