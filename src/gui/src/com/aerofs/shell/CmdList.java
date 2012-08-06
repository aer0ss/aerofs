package com.aerofs.shell;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.KIndex;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Ritual.GetChildrenAttributesReply;
import com.aerofs.proto.Ritual.ListRevChildrenReply;
import com.aerofs.proto.Ritual.PBBranch;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import com.aerofs.proto.Ritual.PBRevChild;

public class CmdList implements IShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        boolean longFormat = cl.hasOption('l');
        boolean history = cl.hasOption('r');

        ArrayList<List<String>> paths = new ArrayList<List<String>>();
        for (String arg : cl.getArgs()) {
            paths.add(s.d().buildPathElemList_(arg));
        }

        if (paths.isEmpty()) paths.add(s.d().getPwdElements_());

        for (List<String> path : paths) {
            if (history) {
                listHistory(s, path, longFormat);
            } else {
                list(s, path, longFormat);
            }
        }
    }

    private static void list(ShellCommandRunner<ShProgram> s,
                             List<String> path, boolean longFormat)
            throws Exception
    {
        PBPath parent = ShProgram.buildPath_(path);
        GetChildrenAttributesReply reply = s.d().getRitualClient_().getChildrenAttributes(
                Cfg.user(), parent);

        for (int i = 0; i < reply.getChildrenNameCount(); i++) {
            String name = reply.getChildrenName(i);
            PBObjectAttributes oa = reply.getChildrenAttributes(i);

            if (!longFormat) {
                s.out().println(name);
            } else {
                char c1 = '-';
                char c2 = oa.getExcluded() ? 'X' : '-';
                String c3 = " ";
                switch (oa.getType()) {
                case FILE:
                    if (!oa.getExcluded() && oa.getBranchCount() == 0) {
                        c2 = 'x';
                    } else if (oa.getBranchCount() > 1) {
                        c3 = String.valueOf(oa.getBranch(0).getKidx());
                    }
                    break;
                case FOLDER:
                    c1 = 'd';
                    break;
                case SHARED_FOLDER:
                    c1 = 's';
                    break;
                default:
                    assert false;
                }

                StringBuilder sb = new StringBuilder().append(c1).append(c2).append(c3);

                sb.append(' ').append(name);

                s.out().println(sb);

                // print branches
                for (PBBranch b : oa.getBranchList()) {
                    if (b.getKidx() == KIndex.MASTER.getInt()) continue;
                    s.out().println("" + c1 + c2 + b.getKidx());
                }
            }
        }
    }

    private static void listHistory(ShellCommandRunner<ShProgram> s,
                                    List<String> path, boolean longFormat)
            throws Exception
    {
        PBPath parent = ShProgram.buildPath_(path);
        ListRevChildrenReply reply = s.d().getRitualClient_().listRevChildren(parent);

        for (PBRevChild child : reply.getChildList()) {
            if (longFormat) {
                s.out().println((child.getIsDir() ? "d" : "-") + "-  " + child.getName());
            } else {
                s.out().println(child.getName());
            }
        }
    }

    @Override
    public String getName()
    {
        return "ls";
    }

    @Override
    public String getDescription()
    {
        return "list directory information";
    }

    @Override
    public String getOptsSyntax()
    {
        return "[PATH]...";
    }

    @Override
    public Options getOpts()
    {
        return new Options()
                .addOption("l", "long", false, "use long format")
                .addOption("r", "revision", false, "list revision tree");
    }
}
