package com.aerofs.tools;

import com.aerofs.lib.bf.BFOID;
import com.aerofs.ids.OID;

public class ToolBFOID implements ITool {

    @Override
    public void run(String[] args) throws Exception
    {
        for (String arg : args) {
            BFOID filter = new BFOID();
            filter.add_(new OID(arg));
            System.out.println(filter);
        }
    }

    @Override
    public String getName()
    {
        return "bfoid";
    }
}
