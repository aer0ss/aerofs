/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.tools;

import com.aerofs.lib.SecUtil;
import com.aerofs.lib.cfg.Cfg;

public class ToolKey implements ITool
{
    @Override
    public void run(String[] args)
            throws Exception
    {
        Cfg.init_(Cfg.absRTRoot(), true);
        System.out.println(SecUtil.exportPrivateKey(Cfg.privateKey()));
    }

    @Override
    public String getName()
    {
        return "key";
    }
}
