package com.aerofs.shell;

import com.aerofs.lib.Path;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.RitualNotifications.PBDownloadEvent;
import com.aerofs.proto.RitualNotifications.PBDownloadEvent.State;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBUploadEvent;
import com.aerofs.ui.RitualNotificationClient;
import com.aerofs.ui.RitualNotificationClient.IListener;
import com.aerofs.ui.UIUtil;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CmdTransfers implements IShellCommand<ShProgram>
{
    @Override
    public void execute(final ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        boolean debug = false;
        for (String arg : cl.getArgs()) {
            if (arg.equals(ShProgram.DEBUG_FLAG)) {
                debug = true;
            }
        }

        final boolean debugFinal = debug;
        RitualNotificationClient rnc = new RitualNotificationClient();
        rnc.addListener(new IListener() {
            @Override
            public void onNotificationReceived(PBNotification pb)
            {
                print(pb, s.out(), debugFinal);
            }
        });

        rnc.start();

        ThreadUtil.sleepUninterruptable(Long.MAX_VALUE);
    }

    @Override
    public String getName()
    {
        return "transfers";
    }

    @Override
    public String getDescription()
    {
        return "shows active file transfers until the process is killed";
    }

    @Override
    public String getOptsSyntax()
    {
        return "";
    }

    @Override
    public Options getOpts()
    {
        return ShellCommandRunner.EMPTY_OPTS;
    }

    public void print(PBNotification pb, PrintStream ps, boolean debug)
    {
        switch (pb.getType()) {
        case DOWNLOAD:
            printDownload(pb.getDownload(), ps, debug);
            break;
        case UPLOAD:
            printUpload(pb.getUpload(), ps, debug);
            break;
        default:
        }
    }

    private void printTime(PrintStream ps)
    {
        ps.print(new SimpleDateFormat().format(new Date()) + " | ");
    }

    private void printUpload(PBUploadEvent ev, PrintStream ps, boolean debug)
    {
        printTime(ps);
        ps.print("UL | ");

        // print percentage
        ps.print(ev.getDone() * 100 / ev.getTotal());
        ps.print("%\t| ");

        // print device id and sockid
        if (debug) {
            ps.print(new DID(ev.getDeviceId()).toStringFormal() + "\t| ");
            ps.print(new SOCID(ev.getSocid()) + "\t| ");
        }

        // print path
        String str;
        if (debug) {
            str = new Path(ev.getPath()).toString();
        } else {
            Path path = new Path(ev.getPath());
            str = UIUtil.getUserFriendlyPath(ev.getSocid(), ev.getPath(), path);
        }
        ps.println(str);
    }

    private void printDownload(PBDownloadEvent ev, PrintStream ps, boolean debug)
    {
        printTime(ps);
        ps.print("DL | ");

        // print percentage
        if (ev.getState() == State.ONGOING) {
            ps.print(ev.getDone() * 100 / ev.getTotal());
            ps.print("%");
        } else {
            ps.print(ev.getState().toString().toLowerCase());
        }
        ps.print("\t| ");

        // print sockid
        if (debug) ps.print(new SOCID(ev.getSocid()) + "\t| ");

        // print path
        String str;
        if (debug) {
            str = new Path(ev.getPath()).toString();
        } else {
            Path path = new Path(ev.getPath());
            str = UIUtil.getUserFriendlyPath(ev.getSocid(), ev.getPath(), path);
        }
        ps.println(str);
    }
}
