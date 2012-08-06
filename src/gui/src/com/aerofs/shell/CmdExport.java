package com.aerofs.shell;

import java.io.File;
import java.io.PrintStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExBadArgs;
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
        return "export a file to the local file system, either at its current version or at a past version";
    }

    @Override
    public Options getOpts()
    {
        return ShellCommandRunner.EMPTY_OPTS;
    }

    @Override
    public String getOptsSyntax()
    {
        return "SOURCE DEST [REVINDEX]";
    }

    class Downloader
    {
        private final RitualBlockingClient _ritualClient;
        private final String _user;
        private final PrintStream _out;

        Downloader(ShellCommandRunner<ShProgram> s)
        {
            _ritualClient = s.d().getRitualClient_();
            _user = Cfg.user();
            _out = s.out();
        }

        void download(Path source, File dest) throws Exception
        {
            GetObjectAttributesReply objectAttributesReply = _ritualClient.getObjectAttributes(_user, source.toPB());
            PBObjectAttributes oa = objectAttributesReply.getObjectAttributes();
            download(source, oa, dest);
        }

        void download(Path source, PBObjectAttributes oa, File dest) throws Exception
        {
            switch (oa.getType()) {
                case FILE: {
                    _out.format("Exporting %s to %s...\n", source, dest);
                    ExportFileReply reply = _ritualClient.exportFile(source.toPB());
                    File temp = new File(reply.getDest());
                    FileUtil.moveInOrAcrossFileSystem(temp, dest);
                    break;
                }

                case FOLDER:
                case SHARED_FOLDER: {
                    FileUtil.mkdir(dest);
                    GetChildrenAttributesReply reply = _ritualClient.getChildrenAttributes(_user, source.toPB());
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
        if (args.length < 2 || args.length > 3) throw new ExBadArgs();

        Path source = new Path(s.d().buildPathElemList_(args[0]));
        File dest = new File(args[1]);
        if (dest.isDirectory() && !source.isEmpty()) {
            dest = new File(dest, source.last());
        }

        if (args.length == 3) {
            ExportRevisionReply reply = s.d().getRitualClient_()
                                             .exportRevision(source.toPB(),
                                                             ByteString.copyFromUtf8(args[2]));
            FileUtil.moveInOrAcrossFileSystem(new File(reply.getDest()), dest);
        } else {
            Downloader downloader = new Downloader(s);
            downloader.download(source, dest);
        }
    }
}
