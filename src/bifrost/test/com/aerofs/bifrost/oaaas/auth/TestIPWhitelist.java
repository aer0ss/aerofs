package com.aerofs.bifrost.oaaas.auth;

import com.aerofs.bifrost.server.BifrostTest;
import com.aerofs.lib.LibParam.MobileDeviceManagement;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class TestIPWhitelist extends BifrostTest
{
    private boolean _enabled_cached_value;
    private String _proxies_cached_value;
    private String wont_match_anything = "127.0.0.1/32";
    private String will_match = "192.0.0.0/8";
    private String will_match_single_ip = "192.168.1.1/32";
    private String multiple_cidrs = "127.0.0.0/24;192.168.0.0/16";

    @Before
    public void cacheValues() {
        _enabled_cached_value = MobileDeviceManagement.IS_ENABLED;
        _proxies_cached_value = MobileDeviceManagement.MDM_PROXIES;
    }

    @After
    public void restoreValues() {
        MobileDeviceManagement.IS_ENABLED = _enabled_cached_value;
        MobileDeviceManagement.MDM_PROXIES = _proxies_cached_value;
    }

    @Test
    public void willScreenMobile() throws Exception {
        MobileDeviceManagement.IS_ENABLED = true;
        MobileDeviceManagement.MDM_PROXIES = wont_match_anything;
        com.aerofs.bifrost.oaaas.auth.MobileDeviceManagement.refreshCIDRBlocks();
        boolean authorized = com.aerofs.bifrost.oaaas.auth.MobileDeviceManagement.isWhitelistedIP("192.168.1.1");
        assertFalse(authorized);
    }

    @Test
    public void willAcceptMatchingMobile() throws Exception {
        MobileDeviceManagement.IS_ENABLED = true;
        MobileDeviceManagement.MDM_PROXIES = will_match;
        com.aerofs.bifrost.oaaas.auth.MobileDeviceManagement.refreshCIDRBlocks();
        boolean authorized = com.aerofs.bifrost.oaaas.auth.MobileDeviceManagement.isWhitelistedIP("192.168.1.1");
        assertTrue(authorized);
    }

    @Test
    public void willAcceptExactIP() throws Exception {
        MobileDeviceManagement.IS_ENABLED = true;
        MobileDeviceManagement.MDM_PROXIES = will_match_single_ip;
        com.aerofs.bifrost.oaaas.auth.MobileDeviceManagement.refreshCIDRBlocks();
        boolean authorized = com.aerofs.bifrost.oaaas.auth.MobileDeviceManagement.isWhitelistedIP("192.168.1.1");
        assertTrue(authorized);
    }

    @Test
    public void willWorkWithMultipleProxies() throws Exception {
        MobileDeviceManagement.IS_ENABLED = true;
        MobileDeviceManagement.MDM_PROXIES = multiple_cidrs;
        com.aerofs.bifrost.oaaas.auth.MobileDeviceManagement.refreshCIDRBlocks();
        boolean authorized = com.aerofs.bifrost.oaaas.auth.MobileDeviceManagement.isWhitelistedIP("192.168.1.1");
        assertTrue(authorized);

        authorized = com.aerofs.bifrost.oaaas.auth.MobileDeviceManagement.isWhitelistedIP("1.1.1.1");
        assertFalse(authorized);
    }

    @Test
    public void willCatchIPv6() throws Exception {
        MobileDeviceManagement.IS_ENABLED = true;
        MobileDeviceManagement.MDM_PROXIES = multiple_cidrs;
        com.aerofs.bifrost.oaaas.auth.MobileDeviceManagement.refreshCIDRBlocks();
        boolean authorized = com.aerofs.bifrost.oaaas.auth.MobileDeviceManagement.isWhitelistedIP("192.168.1.1");
        assertTrue(authorized);

        authorized = com.aerofs.bifrost.oaaas.auth.MobileDeviceManagement.isWhitelistedIP("abcd:1234:5678::");
        assertFalse(authorized);
    }
}
