/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.lib.LibParam.LicenseProperties;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.lib.ex.ExInvalidEmailAddress;
import com.aerofs.lib.ex.ExNotAuthenticated;
import com.aerofs.proto.Common.Void;
import com.aerofs.sp.server.lib.License;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.mockito.Mockito.*;

public class TestSP_SendPriorityDefectEmail extends AbstractSPTest
{
    // used to back up default support email address.
    private String _supportEmail;

    private static final String DEFECT_ID = "0000deadbeef00000000deadbeef0000";

    private void setupOnSiteProperties()
            throws Exception
    {
        // back up the previous support e-mail address
        _supportEmail = WWW.SUPPORT_EMAIL_ADDRESS;

        WWW.SUPPORT_EMAIL_ADDRESS = "support@myplops.com";

        Properties properties = new Properties();
        properties.setProperty(LicenseProperties.CUSTOMER_ID, "9001");
        properties.setProperty(LicenseProperties.CUSTOMER_NAME, "MyPlops Inc.");
        ConfigurationProperties.setProperties(properties);
        PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT = true;

        // renew license is necessary to reload license properties
        license = spy(new License());
        mockLicense();
        rebuildSPService();
    }

    private void restoreDefaultProperties()
    {
        WWW.SUPPORT_EMAIL_ADDRESS = _supportEmail;

        ConfigurationProperties.setProperties(new Properties());
        PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT = false;

        // renew license is necessary to reload license properties
        license = spy(new License());
        mockLicense();
        rebuildSPService();
    }

    @Before
    public void setup()
            throws Exception
    {
        User sessionUser;
        sqlTrans.begin();
        sessionUser = saveUser();
        sqlTrans.commit();
        setSession(sessionUser);

        ListenableFuture<Void> nothing =
                UncancellableFuture.createSucceeded(Void.getDefaultInstance());
        doReturn(nothing).when(asyncEmailSender).sendPublicEmailFromSupport(anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    public void shouldQueueSendEmailRequestWithDefault()
            throws Exception
    {
        service.sendPriorityDefectEmail(DEFECT_ID, "replyto@example.com", "Plops", "My plops don't work!");

        verify(asyncEmailSender, times(1)).sendPublicEmailFromSupport(
                eq("AeroFS"),
                eq("support@aerofs.com"),
                eq("replyto@example.com"),
                eq("[AeroFS Support] Plops"),
                anyString(),
                anyString());
    }

    @Test
    public void shouldQueueSendEmailRequestWithOnsite()
            throws Exception
    {
        setupOnSiteProperties();

        try {
            service.sendPriorityDefectEmail(DEFECT_ID, "replyto@example.com", "Plops", "My plops don't work!");

            verify(asyncEmailSender, times(1)).sendPublicEmailFromSupport(
                    eq("AeroFS"),
                    eq("support@myplops.com"),
                    eq("replyto@example.com"),
                    eq("[AeroFS Support] Plops"),
                    anyString(),
                    anyString());
        } finally {
            restoreDefaultProperties();
        }
    }

    @Test(expected = ExNotAuthenticated.class)
    public void shouldThrowIfNotAuthenticated()
            throws Exception
    {
        // N.B. remove does not throw ExNotAuthenticated, so if ExNotAuthenticated is thrown
        //   it must be thrown from sendDryadEmail.
        session.deauthorize();

        service.sendPriorityDefectEmail(DEFECT_ID, "replyto@example.com", "Plops", "My plops don't work!");
    }

    @Test(expected = ExInvalidEmailAddress.class)
    public void shouldThrowOnInvalidContactEmailAddress()
            throws Exception
    {
        service.sendPriorityDefectEmail(DEFECT_ID, "call me plops", "Plops", "My plops will go on");
    }
}
