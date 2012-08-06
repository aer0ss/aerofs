package com.aerofs.tools;

import com.aerofs.lib.Util;

public class ToolCRC32 implements ITool {

    @Override
    public void run(String[] args) throws Exception
    {
        for (String arg : args) {
            String[] tokens = arg.split("[/\\\\]");
            for (String token : tokens) {
                System.out.print(Util.crc32(token));
                System.out.print(' ');
            }
            System.out.println();
        }
    }

    @Override
    public String getName()
    {
        return "crc32";
    }
}
