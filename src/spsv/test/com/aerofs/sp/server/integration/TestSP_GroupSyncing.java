/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.lib.LibParam.Identity;
import com.aerofs.lib.LibParam.Identity.Authenticator;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import java.time.Clock;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class TestSP_GroupSyncing extends AbstractSPTest
{
    @Test
    public void shouldThrowIfNotAdmin()
            throws Exception
    {
        sqlTrans.begin();
        User admin = saveUser();
        User user1 = saveUser();
        user1.setOrganization(admin.getOrganization(), AuthorizationLevel.USER);
        sqlTrans.commit();
        setSession(user1);

        try {
            service.syncGroupsWithLDAPEndpoint();
            fail();
        } catch (ExNoPerm e) {}
    }

    @Captor ArgumentCaptor<Long> delayCaptor;

    @Test
    public void shouldSyncAtSpecifiedTime()
    {
        Properties groupSyncingEnabled = new Properties();
        groupSyncingEnabled.setProperty("ldap.groupsyncing.enabled", "true");
        Authenticator prevValue = Identity.AUTHENTICATOR;
        Identity.AUTHENTICATOR = Authenticator.EXTERNAL_CREDENTIAL;
        groupSyncingEnabled.setProperty("ldap.groupsyncing.time", "00:00");
        ConfigurationProperties.setProperties(groupSyncingEnabled);
        rebuildSPService();

        LocalTime now = LocalTime.now(Clock.systemUTC());
        verify(scheduledExecutorService).scheduleAtFixedRate(any(), delayCaptor.capture(),
                eq((long)(60*60*24)), eq(TimeUnit.SECONDS));
        long delay = delayCaptor.getValue();
        assertTrue(delay > 0);
        LocalTime scheduledTime = now.plusSeconds(delay);
        long second = scheduledTime.until(LocalTime.MIDNIGHT, ChronoUnit.SECONDS);
        if (second < 0) {
            // there are 86400 seconds in a day, so this means it's within 10 seconds after midnight
            assertTrue("syncing scheduled to go off too late", second < -86390);
        } else {
            // within 10 seconds before midnight
            assertTrue("syncing scheduled to go off too early", second < 10);
        }

        Identity.AUTHENTICATOR = prevValue;
    }

    @Test
    public void shouldRequireLdapToBeEnabled()
    {
        Authenticator prevValue = Identity.AUTHENTICATOR;
        Identity.AUTHENTICATOR = Authenticator.LOCAL_CREDENTIAL;
        rebuildSPService();

        assertEquals(service.LDAP_GROUP_SYNCING_ENABLED, false);
        verify(scheduledExecutorService, never())
                .scheduleAtFixedRate(any(), anyLong(), anyLong(), any());

        Identity.AUTHENTICATOR = prevValue;
    }

    @Test
    public void shouldUseGroupsyncingEnabledConfig()
    {
        Properties groupSyncingDisabled = new Properties();
        groupSyncingDisabled.setProperty("ldap.groupsyncing.enabled", "false");
        ConfigurationProperties.setProperties(groupSyncingDisabled);
        rebuildSPService();

        assertEquals(service.LDAP_GROUP_SYNCING_ENABLED, false);
        verify(scheduledExecutorService, never())
                .scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
    }

}
