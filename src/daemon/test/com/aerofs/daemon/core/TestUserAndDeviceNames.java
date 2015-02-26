/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core;

import com.aerofs.base.C;
import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
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
import org.mockito.Mock;

import static java.util.Collections.emptyList;
import static org.mockito.Mockito.*;

public class TestUserAndDeviceNames extends AbstractTest
{
    @Mock CfgLocalUser user;
    @Mock TokenManager tokenManager;
    @Mock TransManager tm;
    @Mock DeviceToUserMapper d2u;
    @Mock IUserAndDeviceNameDatabase udndb;
    @Mock InjectableSPBlockingClientFactory factSP;
    @Mock ElapsedTimer.Factory factTimer;
    @Mock ElapsedTimer timer;
    @Mock Token tk;
    @Mock SPBlockingClient spClient;

    UserAndDeviceNames _udn;

    @Before
    public void setup() throws Exception
    {
        when(user.get()).thenReturn(UserID.fromInternal("test@aerofs.com"));
        when(tokenManager.acquire_(any(Cat.class), any(String.class))).thenReturn(tk);
        doThrow(new ExBadCredential()).when(spClient).signInRemote();
        when(factSP.create()).thenReturn(spClient);
        when(factTimer.create()).thenReturn(timer);

        _udn = spy(new UserAndDeviceNames(user, tokenManager, tm, d2u, udndb, factSP, factTimer));
        _udn.setSPLoginDelay(30 * C.MIN);
    }

    @Test
    public void shouldThrottleToOnce() throws Exception
    {
        _udn.updateLocalDeviceInfo_(emptyList());
        when(timer.elapsed()).thenReturn(2 * C.SEC);
        _udn.updateLocalDeviceInfo_(emptyList());
        when(timer.elapsed()).thenReturn(3 * C.SEC);
        _udn.updateLocalDeviceInfo_(emptyList());
        when(timer.elapsed()).thenReturn(4 * C.SEC);
        _udn.updateLocalDeviceInfo_(emptyList());
        when(timer.elapsed()).thenReturn(5 * C.SEC);
        _udn.updateLocalDeviceInfo_(emptyList());

        verify(_udn, times(1)).updateLocalDeviceInfoImpl_(anyListOf(DID.class));
    }

    @Test
    public void shouldThrottleToTwice() throws Exception
    {
        _udn.updateLocalDeviceInfo_(emptyList());
        when(timer.elapsed()).thenReturn(9 * C.MIN);
        _udn.updateLocalDeviceInfo_(emptyList());
        when(timer.elapsed()).thenReturn(20 * C.MIN);
        _udn.updateLocalDeviceInfo_(emptyList());
        verify(factTimer).create();
        when(timer.elapsed()).thenReturn(10 * C.MIN);
        _udn.updateLocalDeviceInfo_(emptyList());
        when(timer.elapsed()).thenReturn(20 * C.MIN);
        _udn.updateLocalDeviceInfo_(emptyList());
        when(timer.elapsed()).thenReturn(50 * C.MIN);
        _udn.updateLocalDeviceInfo_(emptyList());

        verify(_udn, times(2)).updateLocalDeviceInfoImpl_(anyListOf(DID.class));
    }

    @Test
    public void shouldNotThrottle() throws Exception
    {
        _udn.updateLocalDeviceInfo_(emptyList());
        when(timer.elapsed()).thenReturn(100 * C.MIN);
        _udn.updateLocalDeviceInfo_(emptyList());
        verify(factTimer).create();
        when(timer.elapsed()).thenReturn(100 * C.MIN);
        _udn.updateLocalDeviceInfo_(emptyList());
        verify(factTimer).create();
        when(timer.elapsed()).thenReturn(100 * C.MIN);
        _udn.updateLocalDeviceInfo_(emptyList());
        verify(factTimer).create();
        when(timer.elapsed()).thenReturn(100 * C.MIN);
        _udn.updateLocalDeviceInfo_(emptyList());
        verify(factTimer).create();

        verify(_udn, times(5)).updateLocalDeviceInfoImpl_(anyListOf(DID.class));
    }
}
