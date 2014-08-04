package com.aerofs.bifrost.oaaas.auth;

import com.aerofs.base.Loggers;
import com.aerofs.lib.LibParam;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.apache.commons.net.util.SubnetUtils;
import org.slf4j.Logger;

import java.util.List;

public class MobileDeviceManagement
{

    private static final Logger l = Loggers.getLogger(MobileDeviceManagement.class);
    private static List<SubnetUtils> _MDMProxies;

    private synchronized static void loadProxies()
    {
        if (_MDMProxies == null) {
            _MDMProxies = parseProxies(LibParam.MobileDeviceManagement.MDM_PROXIES);
        }
    }

    //input should be a semicolon separated list of CIDR addresses
    private static List<SubnetUtils> parseProxies(String s)
    {
        Splitter splitter = Splitter.on(";").omitEmptyStrings().trimResults();
        Iterable<String> CIDRs = splitter.split(s);
        List<SubnetUtils> parsedCIDRs = Lists.newArrayList();
        for (String subnet: CIDRs) {
            try {
                SubnetUtils parsed = new SubnetUtils(subnet);
                //in case the subnet is fully specified, it will still match 1 IP addr
                parsed.setInclusiveHostCount(true);
                parsedCIDRs.add(parsed);
            } catch (IllegalArgumentException e) {
                l.warn("malformed CIDR notation in configuration: " + subnet +
                        ", excluding from whitelisted subnets");
            }
        }
        return parsedCIDRs;
    }

    public static boolean isWhitelistedIP(String remoteAddress)
    {
        if (remoteAddress == null || remoteAddress.isEmpty()) {
            l.warn("register device call with no remoteAddress");
            return false;
        } else if (fromMDMProxy(remoteAddress)) {
            return true;
        }else{
            return false;
        }
    }

    public static boolean isMDMEnabled()
    {
        return LibParam.MobileDeviceManagement.IS_ENABLED;
    }

    protected static boolean fromMDMProxy(String remoteAddress)
    {
        loadProxies();
        for(SubnetUtils subnet: _MDMProxies) {
            try {
                if (subnet.getInfo().isInRange(remoteAddress)) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                l.warn("Mobile Device Management only supports IPv4 addresses");
                return false;
            }
        }
        return false;
    }

    //for testing purposes
    synchronized static void refreshCIDRBlocks()
    {
        _MDMProxies = null;
        loadProxies();
    }
}
