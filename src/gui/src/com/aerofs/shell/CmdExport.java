package com.aerofs.shell;

import java.io.File;
import java.io.PrintStream;

import com.aerofs.lib.id.KIndex;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Ritual.ExportConflictReply;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.proto.Ritual.ExportFileReply;
import com.aerofs.proto.Ritual.ExportRevisionReply;
import com.aerofs.proto.Ritual.GetChildrenAttributesReply;
import com.aerofs.proto.Ritual.GetObjectAttributesReply;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import com.google.protobuf.ByteString;

public class CmdExport implements IShellCommand<ShProgram>
{
    @Override
    public String getName()
    {
        return "export";
    }

    @Override
    public String getDescription()
    {
        return "export a file to the local file system (current, past or conflict version)";
    }

    @Override
    public Options getOpts()
    {
        return new Options()
                .addOption("h", "history", true, "export a past version. the argument" +
                        " specifies the version index. see also 'ls -h' and 'vh' commands")
                .addOption("b", "branch", true, "export a conflict branch. the argument" +
                        " specifies the conflict branch. see also 'conflicts' command");
    }

    @Override
    public String getOptsSyntax()
    {
        return "SOURCE DEST";
    }

    class Downloader
    {
        private final RitualBlockingClient _ritual;
        private final UserID _userId;
        private final PrintStream _out;

        Downloader(ShellCommandRunner<ShProgram> s)
        {
            _ritual = s.d().getRitualClient_();
            _userId = Cfg.user();
            _out = s.out();
        }

        void download(Path source, File dest) throws Exception
        {
            GetObjectAttributesReply objectAttributesReply =
                    _ritual.getObjectAttributes(_userId.getString(), source.toPB());
            PBObjectAttributes oa = objectAttributesReply.getObjectAttributes();
            download(source, oa, dest);
        }

        void download(Path source, PBObjectAttributes oa, File dest) throws Exception
        {
            switch (oa.getType()) {
                case FILE: {
                    _out.format("Exporting %s to %s...\n", source, dest);
                    ExportFileReply reply = _ritual.exportFile(source.toPB());
                    File temp = new File(reply.getDest());
                    FileUtil.moveInOrAcrossFileSystem(temp, dest);
                    break;
                }

                case FOLDER:
                case SHARED_FOLDER: {
                    FileUtil.mkdir(dest);
                    GetChildrenAttributesReply reply = _ritual.getChildrenAttributes(
                            _userId.getString(), source.toPB());
                    int count = reply.getChildrenNameCount();
                    assert count == reply.getChildrenAttributesCount();
                    for (int i = 0; i < count; ++i) {
                        String name = reply.getChildrenName(i);
                        PBObjectAttributes childAttr = reply.getChildrenAttributes(i);
                        Path childSource = source.append(name);
                        File childDest = new File(dest, name);
                        download(childSource, childAttr, childDest);
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl) throws Exception
    {
        String[] args = cl.getArgs();
        if (args.length != 2) throw new ExBadArgs();

        String revIndex = cl.getOptionValue('h');
        String conflict = cl.getOptionValue('b');

        if (revIndex != null && conflict != null) {
            throw new ExBadArgs("history and conflict options are mutually exclusive");
        }

        Path source = new Path(s.d().buildPathElemList_(args[0]));
        File dest = new File(args[1]);
        if (dest.isDirectory() && !source.isEmpty()) {
            dest = new File(dest, source.last());
        }

        if (revIndex != null) {
            ExportRevisionReply reply = s.d().getRitualClient_()
                    .exportRevision(source.toPB(), ByteString.copyFromUtf8(revIndex));
            FileUtil.moveInOrAcrossFileSystem(new File(reply.getDest()), dest);
        } else if (conflict != null) {
            int kidx = Integer.valueOf(conflict);
            if (kidx == KIndex.MASTER.getInt()) throw new ExBadArgs("");
            ExportConflictReply reply = s.d().getRitualClient_()
                    .exportConflict(source.toPB(), kidx);
            FileUtil.moveInOrAcrossFileSystem(new File(reply.getDest()), dest);
        } else {
            Downloader downloader = new Downloader(s);
            downloader.download(source, dest);
        }
    }
}
