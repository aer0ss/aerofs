/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.net;

import org.jboss.netty.channel.Channel;

public abstract class ChannelUtil
{
    private ChannelUtil()
    {
        // private to enforce uninstantiability
    }

    public static String pretty(Channel c)
    {
        String hex = Integer.toHexString(c.getId());
        return String.format("0x%1$8s", hex).replace(' ', '0');
    }
}
