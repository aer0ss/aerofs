package com.aerofs.tools;

import com.aerofs.lib.cfg.Cfg;

public class ToolCert implements ITool {

    @Override
    public void run(String[] args) throws Exception
    {
        System.out.println(Cfg.cert().toString());
    }

    @Override
    public String getName()
    {
        return "cert";
    }
}
