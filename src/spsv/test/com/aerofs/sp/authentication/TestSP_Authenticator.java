/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.ids.UserID;
import com.aerofs.servlets.lib.db.IThreadLocalTransaction;
import com.aerofs.sp.server.integration.AbstractSPTest;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestSP_Authenticator extends AbstractSPTest
{
    @Mock LdapAuthority _remoteAuth;
    @Mock LocalAuthority _localAuth;
    @Mock IThreadLocalTransaction<SQLException> _trans;
    User _user = newUser();

    @Test
    public void authenticateUser_shouldFallThrough() throws Exception
    {
        when(_remoteAuth.canAuthenticate(eq(_user.id()))).thenReturn(false);
        when(_localAuth.canAuthenticate(eq(_user.id()))).thenReturn(false);
        Authenticator _tester = new Authenticator(new IAuthority[]{_remoteAuth, _localAuth});

        try {
            _tester.authenticateUser(_user, CRED, _trans, Authenticator.CredentialFormat.TEXT);
            fail("Requires exception");
        } catch (ExBadCredential expected) { }

        verify(_remoteAuth, times(1)).canAuthenticate(_user.id());
        verify(_remoteAuth, never()).authenticateUser(_user, CRED, _trans, Authenticator.CredentialFormat.TEXT);
        verify(_localAuth, times(1)).canAuthenticate(_user.id());
        verify(_localAuth, never()).authenticateUser(_user, CRED, _trans, Authenticator.CredentialFormat.TEXT);

        assertTrue(_tester.isLocallyManaged(_user.id()));
    }

    @Test
    public void authenticateUser_shouldFallbackToLocal() throws Exception
    {
        when(_remoteAuth.canAuthenticate(eq(_user.id()))).thenReturn(false);
        when(_localAuth.canAuthenticate(eq(_user.id()))).thenReturn(true);
        Authenticator _tester = new Authenticator(new IAuthority[]{ _remoteAuth, _localAuth });

        _tester.authenticateUser(_user, CRED, _trans, Authenticator.CredentialFormat.TEXT);

        verify(_remoteAuth, times(1)).canAuthenticate(_user.id());
        verify(_remoteAuth, never()).authenticateUser(_user, CRED, _trans, Authenticator.CredentialFormat.TEXT);
        verify(_localAuth, times(1)).canAuthenticate(_user.id());
        verify(_localAuth, times(1)).authenticateUser(_user, CRED, _trans, Authenticator.CredentialFormat.TEXT);
    }

    @Test
    public void isLocallyManaged_shouldAskAuthority() throws Exception
    {
        when(_remoteAuth.canAuthenticate(eq(_user.id()))).thenReturn(false);
        when(_localAuth.canAuthenticate(eq(_user.id()))).thenReturn(true);
        Authenticator _tester = new Authenticator(new IAuthority[]{ _remoteAuth, _localAuth });

        _tester.isLocallyManaged(_user.id());
        verify(_localAuth, times(1)).managesLocalCredential();
    }

    @Test
    public void isInternalUser_shouldCallFirstAuthority() throws Exception
    {
        when(_remoteAuth.canAuthenticate(eq(_user.id()))).thenReturn(false);
        when(_localAuth.canAuthenticate(eq(_user.id()))).thenReturn(true);
        Authenticator _tester = new Authenticator(new IAuthority[]{ _remoteAuth, _localAuth });

        _tester.isInternalUser(_user.id());

        verify(_remoteAuth, times(1)).isInternalUser(eq(_user.id()));
    }

    @Test
    public void isInternalUser_shouldAcceptTeamServer() throws Exception
    {
        Authenticator _tester = new Authenticator(new IAuthority[]{ _remoteAuth, _localAuth });

        assertTrue(_tester.isInternalUser(UserID.fromInternal(":123456")));
    }

    // ---- Legacy test methods for old-style credentials ----

    /** Remove this test when we can get rid of the legacy signin methods */
    @Test
    public void authenticateUser_shouldNotModifyLegacyCred() throws Exception
    {
        ArgumentCaptor<byte[]> credential = ArgumentCaptor.forClass(byte[].class);
        Authenticator _tester = new Authenticator(new IAuthority[]{ _localAuth });

        when(_localAuth.canAuthenticate(eq(_user.id()))).thenReturn(true);

        _tester.authenticateUser(_user, CRED, _trans, Authenticator.CredentialFormat.LEGACY);

        verify(_localAuth, times(1)).authenticateUser(
                eq(_user), credential.capture(), eq(_trans), eq(Authenticator.CredentialFormat.LEGACY));
        assertEquals(CRED, credential.getValue());
    }
}
