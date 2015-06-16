/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server;

import com.aerofs.base.C;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.sp.server.lib.License;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestLicense
{
    @Before
    public void setupConfiguration()
    {
        ConfigurationProperties.setProperties(new Properties());
    }

    @Test
    public void isValid_shouldReturnFalseIfLicenseIsAbsent()
    {
        assertFalse(new License().isValid());
    }

    @Test
    public void isValid_shouldReturnFalseIfLicenseTypeIsNotNormal()
    {
        setProperties("abnormal", 1);
        assertFalse(new License().isValid());
    }

    @Test
    public void isValid_shouldReturnFalseIfLicenseValidUntilIsAbsent()
    {
        setProperties("normal", null);
        assertFalse(new License().isValid());
    }

    @Test
    public void isValid_shouldReturnFalseIfLicenseHasExpired()
    {
        setProperties("normal", -1);
        assertFalse(new License().isValid());
        setProperties("normal", -30);
        assertFalse(new License().isValid());
        setProperties("normal", -365);
        assertFalse(new License().isValid());
    }

    @Test
    public void isValid_shouldReturnTrueIfLicenseIsValid()
    {
        setProperties("normal", 1);
        assertTrue(new License().isValid());
        setProperties("normal", 30);
        assertTrue(new License().isValid());
        setProperties("normal", 365);
        assertTrue(new License().isValid());
    }

    /**
     * @param expiryOffsetDays the number of days after which the license should expire starting
     * today. It can be negative to indicate already expired licenses.
     */
    private void setProperties(@Nullable String type, @Nullable Integer expiryOffsetDays)
    {
        Properties props = new Properties();
        if (type != null) props.put("license_type", type);
        if (expiryOffsetDays != null) {
            long expiry = System.currentTimeMillis() + expiryOffsetDays * C.DAY;
            String str = new SimpleDateFormat("yyyy-MM-dd").format(new Date(expiry));
            props.put("license_valid_until", str);
        }
        ConfigurationProperties.setProperties(props);
    }
}
