/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.common;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.lib.SystemUtil;

import java.net.MalformedURLException;
import java.net.URL;

public class SubscriptionParams
{
    public static final URL UNSUBSCRIPTION_URL;

    public static final String UNSUB_TOKEN = "u";
    static {
        URL url;
        try {
            url = new URL(WWW.DASHBOARD_HOST_URL + "/unsubscribe?" + UNSUB_TOKEN + "=");
        } catch (MalformedURLException e) {
            SystemUtil.fatal(e);
            url = null;
        }
        UNSUBSCRIPTION_URL = url;

    }
}
