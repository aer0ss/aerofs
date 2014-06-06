/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.lib.LibParam.LicenseProperties;
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

public class TestSP_SendDryadEmail extends AbstractSPTest
{
    // used to back up default support email address.
    private String _supportEmail;

    private static final String VALID_DRYAD_ID = "0000DEADBEEF00000000DEADBEEF0000";
    private static final String INVALID_DRYAD_ID = "DEADBEEF";

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

        // renew license is necessary to reload license properties
        license = spy(new License());
        mockLicense();
        rebuildSPService();
    }

    private void restoreDefaultProperties()
    {
        WWW.SUPPORT_EMAIL_ADDRESS = _supportEmail;

        ConfigurationProperties.setProperties(new Properties());

        // renew license is necessary to reload license properties
        license = spy(new License());
        mockLicense();
        rebuildSPService();
    }

    @Before
    public void setup()
            throws Exception
    {
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        setSession(user);

        ListenableFuture<Void> nothing =
                UncancellableFuture.createSucceeded(Void.getDefaultInstance());
        doReturn(nothing).when(asyncEmailSender).sendPublicEmailFromSupport(anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    public void shouldQueueSendEmailRequestWithDefault()
            throws Exception
    {
        service.sendDryadEmail(VALID_DRYAD_ID, "replyto@example.com", "My plops don't work!");

        verify(asyncEmailSender, times(1)).sendPublicEmailFromSupport(eq("AeroFS"),
                eq("support@aerofs.com"), eq("replyto@example.com"),
                eq("AeroFS Problem #0000DEADBEEF00000000DEADBEEF0000"), eq("Customer ID: N/A\n" +
                        "Customer Name: Unknown\n" +
                        "Contact Email: replyto@example.com\n\n" +
                        "My plops don't work!"), isNull(String.class)
        );
    }

    @Test
    public void shouldQueueSendEmailRequestWithOnsite()
            throws Exception
    {
        setupOnSiteProperties();

        service.sendDryadEmail(VALID_DRYAD_ID, "replyto@example.com", "My plops don't work!");

        verify(asyncEmailSender, times(1)).sendPublicEmailFromSupport(
                eq("AeroFS"),
                eq("support@myplops.com"),
                eq("replyto@example.com"),
                eq("AeroFS Problem #0000DEADBEEF00000000DEADBEEF0000"),
                eq("Customer ID: 9001\n" +
                        "Customer Name: MyPlops Inc.\n" +
                        "Contact Email: replyto@example.com\n\n" +
                        "My plops don't work!"),
                isNull(String.class)
        );

        restoreDefaultProperties();
    }

    @Test(expected = ExNotAuthenticated.class)
    public void shouldThrowIfNotAuthenticated()
            throws Exception
    {
        // N.B. remove does not throw ExNotAuthenticated, so if ExNotAuthenticated is thrown
        //   it must be thrown from sendDryadEmail.
        session.remove();

        service.sendDryadEmail(VALID_DRYAD_ID, "replyto@example.com", "My plops don't work!");
    }

    @Test(expected = ExInvalidEmailAddress.class)
    public void shouldThrowOnInvalidContactEmailAddress()
            throws Exception
    {
        service.sendDryadEmail(VALID_DRYAD_ID, "call me plops", "My plops will go on");
    }

    @Test(expected = ExFormatError.class)
    public void shouldThrownOnInvalidDryadID()
            throws Exception
    {
        service.sendDryadEmail(INVALID_DRYAD_ID, "replyto@example.com", "Plox plops");
    }
}
