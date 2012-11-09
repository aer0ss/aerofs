/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.common;

import com.aerofs.l.L;
import com.aerofs.lib.Util;

import java.net.MalformedURLException;
import java.net.URL;

public class SubscriptionParams
{
    // length of Base62 email ID to be generated
    public static int TOKEN_ID_LENGTH = 12;

    public static final URL UNSUBSCRIPTION_URL;

    public static final String UNSUB_TOKEN = "u";
    static {
        URL url;
        try {
            url = new URL("https://" + L.get().webHost() + "/unsubscribe?" + UNSUB_TOKEN + "=");
        } catch (MalformedURLException e) {
            Util.fatal(e);
            url = null;
        }
        UNSUBSCRIPTION_URL = url;

    }
}
