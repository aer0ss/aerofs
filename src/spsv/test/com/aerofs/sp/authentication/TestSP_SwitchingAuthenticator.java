/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.servlets.lib.db.IThreadLocalTransaction;
import com.aerofs.sp.authentication.IAuthenticator.CredentialFormat;
import com.aerofs.sp.server.integration.AbstractSPTest;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class TestSP_SwitchingAuthenticator extends AbstractSPTest
{
    @Mock LdapAuthenticator _ldapAuth;
    @Mock LocalAuthenticator _localAuth;
    @Mock IThreadLocalTransaction<SQLException> _trans;
    User _user = newUser();

    @Test
    public void shouldAskLdap() throws Exception
    {
        SwitchingAuthenticator _tester = new SwitchingAuthenticator(_ldapAuth, _localAuth);
        _tester.authenticateUser(_user, CRED, _trans, CredentialFormat.TEXT);

        verify(_ldapAuth, times(1)).canAuthenticate(_user);
    }

    @Test
    public void shouldAskLdapProvisioner() throws Exception
    {
        SwitchingAuthenticator _tester = new SwitchingAuthenticator(_ldapAuth, _localAuth);

        when(_ldapAuth.canAuthenticate(eq(_user))).thenReturn(true);
        assertTrue(_tester.isAutoProvisioned(_user));

        verify(_ldapAuth, times(1)).canAuthenticate(_user);
        verifyNoMoreInteractions(_localAuth);
    }

    @Test
    public void shouldNotAskLocal() throws Exception
    {
        SwitchingAuthenticator _tester = new SwitchingAuthenticator(_ldapAuth, _localAuth);

        when(_ldapAuth.canAuthenticate(eq(_user))).thenReturn(false);
        assertFalse(_tester.isAutoProvisioned(_user));

        verify(_ldapAuth, times(1)).canAuthenticate(_user);
        verifyNoMoreInteractions(_localAuth);
    }

    @Test
    public void shouldPreferLdap() throws Exception
    {
        ArgumentCaptor<byte[]> credential = ArgumentCaptor.forClass(byte[].class);
        SwitchingAuthenticator _tester = new SwitchingAuthenticator(_ldapAuth, _localAuth);

        when(_ldapAuth.canAuthenticate(eq(_user))).thenReturn(true);
        _tester.authenticateUser(_user, CRED, _trans, CredentialFormat.TEXT);

        verify(_ldapAuth, times(1)).canAuthenticate(eq(_user));
        verify(_ldapAuth, times(1)).authenticateUser(eq(_user), credential.capture(), eq(_trans),
                eq(CredentialFormat.TEXT));
        verifyNoMoreInteractions(_ldapAuth, _localAuth);

        // Check that we are not scrypt'ing or otherwise messing with LDAP passwords:
        assertEquals(CRED, credential.getValue());
    }

    @Test
    public void shouldFallBackToLocalCredential() throws Exception
    {
        ArgumentCaptor<byte[]> credential = ArgumentCaptor.forClass(byte[].class);
        SwitchingAuthenticator _tester = new SwitchingAuthenticator(_ldapAuth, _localAuth);

        when(_ldapAuth.canAuthenticate(eq(_user))).thenReturn(false);
        _tester.authenticateUser(_user, CRED, _trans, CredentialFormat.TEXT);

        verify(_ldapAuth, times(1)).canAuthenticate(eq(_user));
        verify(_localAuth, times(1)).authenticateUser(eq(_user), credential.capture(), eq(_trans),
                eq(CredentialFormat.TEXT));
        verifyNoMoreInteractions(_ldapAuth, _localAuth);
        assertEquals(CRED, credential.getValue());
    }

    /** Remove this test when we can get rid of the legacy signin methods */
    @Test
    public void shouldFallBackToLocalLegacy() throws Exception
    {
        ArgumentCaptor<byte[]> credential = ArgumentCaptor.forClass(byte[].class);
        SwitchingAuthenticator _tester = new SwitchingAuthenticator(_ldapAuth, _localAuth);

        when(_ldapAuth.canAuthenticate(eq(_user))).thenReturn(false);
        _tester.authenticateUser(_user, CRED, _trans, CredentialFormat.LEGACY);

        verify(_ldapAuth, times(1)).canAuthenticate(eq(_user));
        verify(_localAuth, times(1)).authenticateUser(eq(_user), credential.capture(), eq(_trans),
                eq(CredentialFormat.LEGACY));
        verifyNoMoreInteractions(_ldapAuth, _localAuth);
        assertEquals(CRED, credential.getValue());
    }
}
