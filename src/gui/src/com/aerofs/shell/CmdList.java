package com.aerofs.shell;

import java.util.List;
import java.util.Map.Entry;

import com.aerofs.base.id.SID;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.google.common.collect.Lists;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import com.aerofs.lib.id.KIndex;
import com.aerofs.proto.Ritual.GetChildrenAttributesReply;
import com.aerofs.proto.Ritual.ListRevChildrenReply;
import com.aerofs.proto.Ritual.PBBranch;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import com.aerofs.proto.Ritual.PBRevChild;

public class CmdList extends AbstractShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        boolean longFormat = cl.hasOption('l');
        boolean history = cl.hasOption('h');

        List<Path> paths = Lists.newArrayList();
        for (String arg : cl.getArgs()) {
            paths.add(s.d().buildPath_(arg));
        }

        if (paths.isEmpty()) paths.add(s.d().getPwd_());

        for (Path path : paths) {
            if (history) {
                listHistory(s, path, longFormat);
            } else if (L.isMultiuser() && s.d().isPwdAtUserRoot_()) {
                // special case to make ls more intuitive on Team Server
                listRoots(s, longFormat);
            } else {
                list(s, path, longFormat);
            }
        }
    }

    private static void listRoots(ShellCommandRunner<ShProgram> s, boolean longFormat)
            throws Exception
    {
        for (Entry<SID, String> e : s.d().getRoots().entrySet()) {
            if (longFormat) {
                s.out().println("r-- " + e.getKey().toStringFormal() + " " + e.getValue());
            } else {
                s.out().println(e.getKey().toStringFormal());
            }
        }
    }

    private static void list(ShellCommandRunner<ShProgram> s,
                             Path path, boolean longFormat)
            throws Exception
    {
        GetChildrenAttributesReply reply = s.d().getRitualClient_().getChildrenAttributes(path.toPB());

        for (int i = 0; i < reply.getChildrenNameCount(); i++) {
            String name = reply.getChildrenName(i);
            PBObjectAttributes oa = reply.getChildrenAttributes(i);

            if (!longFormat) {
                s.out().println(name);
            } else {
                char c1 = '-';
                char c2 = oa.getExcluded() ? 'X' : '-';
                char c3 = ' ';
                switch (oa.getType()) {
                case FILE:
                    if (!oa.getExcluded() && oa.getBranchCount() == 0) {
                        c2 = 'x';
                    } else if (oa.getBranchCount() > 1) {
                        c3 = 'c';
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

                // print size and mtime of MASTER branch, if available
                // other branches are exposed through the "conflicts" command
                for (PBBranch b : oa.getBranchList()) {
                    if (b.getKidx() == KIndex.MASTER.getInt()) {
                        sb.append(' ').append(Util.formatSize(b.getLength()));
                        sb.append(' ').append(Util.formatAbsoluteTime(b.getMtime()));
                        break;
                    }
                }

                s.out().println(sb);
            }
        }
    }

    private static void listHistory(ShellCommandRunner<ShProgram> s,
            Path path, boolean longFormat)
            throws Exception
    {
        ListRevChildrenReply reply = s.d().getRitualClient_().listRevChildren(path.toPB());

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
                .addOption("h", "history", false, "list Sync History tree." +
                        " see also 'history' and 'export -h' commands");
    }
}
