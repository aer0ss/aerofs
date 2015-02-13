package com.aerofs.tools;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;

public class ToolRootSID implements ITool {

    @Override
    public void run(String[] args) throws Exception
    {
        if (args.length == 0) System.out.println(Cfg.rootSID().toStringFormal());
        for (String arg : args) System.out.println("rootsid: " + SID.rootSID(UserID.fromExternal(arg))
                .toStringFormal());
    }

    @Override
    public String getName()
    {
        return "rootsid";
    }
}
