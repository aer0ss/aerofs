package com.aerofs.tools;

import java.util.Arrays;
import java.util.Map;
import com.aerofs.lib.IProgram;
import com.google.common.collect.Maps;

public class ToolsProgram implements IProgram
{
    private static final Map<String, ITool> map = Maps.newTreeMap();
    static {
        ITool t;
        t = new ToolCSR();
        map.put(t.getName(), t);
        t = new ToolCRC32();
        map.put(t.getName(), t);
        t = new ToolCert();
        map.put(t.getName(), t);
        t = new ToolRootSID();
        map.put(t.getName(), t);
        t = new ToolBFOID();
        map.put(t.getName(), t);
        t = new ToolCfgDB();
        map.put(t.getName(), t);
    }

    @Override
    public void launch_(String rtRoot, String prog, String[] args)
        throws Exception
    {
        if (args.length == 0) {
            for (String name : map.keySet()) System.out.println(name);
            return;
        }

        ITool tool = map.get(args[0]);
        if (tool == null) throw new ExProgramNotFound(prog);

        tool.run(Arrays.copyOfRange(args, 1, args.length));
    }
}
