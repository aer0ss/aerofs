/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.os;

/**
 * Sigh, PowerMock looked so promising but for some reason it chokes on abstract classes
 */
public class CfgOS
{
    public boolean isWindows()
    {
        return OSUtil.isWindows();
    }
}
