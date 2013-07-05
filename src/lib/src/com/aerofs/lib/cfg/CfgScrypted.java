/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.cfg;

/**
 * Local device's password
 */
public class CfgScrypted
{
    public byte[] get()
    {
        return Cfg.scrypted();
    }
}
