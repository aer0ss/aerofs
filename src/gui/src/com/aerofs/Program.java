// we put it here instead of com.aerofs.ui so ProGuard can rename the entire ui
// package.
//
package com.aerofs;

import com.aerofs.cli.CLIProgram;
import com.aerofs.gui.GUIProgram;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.Param;
import com.aerofs.shell.ShProgram;
import com.aerofs.tools.ToolsProgram;

public class Program implements IProgram {

    @Override
    public void launch_(String rtRoot, String prog, String[] args) throws Exception
    {
        IProgram program;
        if (prog.equals(Param.SH_NAME)) program = new ShProgram();
        else if (prog.equals(Param.GUI_NAME)) program = new GUIProgram();
        else if (prog.equals(Param.CLI_NAME)) program = new CLIProgram();
        else if (prog.equals(Param.TOOLS_NAME)) program = new ToolsProgram();
        else throw new ExProgramNotFound(prog);
        program.launch_(rtRoot, prog, args);
    }
}
