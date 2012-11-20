package com.aerofs.tools;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.SID;

public class ToolRootSID implements ITool {

    @Override
    public void run(String[] args) throws Exception
    {
        if (args.length == 0) System.out.println(Cfg.rootSID().toStringFormal());
        for (String arg : args) System.out.println(SID.rootSID(arg).toStringFormal());
    }

    @Override
    public String getName()
    {
        return "rootsid";
    }
}
