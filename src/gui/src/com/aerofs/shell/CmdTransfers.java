package com.aerofs.shell;

import com.aerofs.base.id.DID;
import com.aerofs.lib.Path;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.proto.RitualNotifications.PBTransferEvent;
import com.aerofs.ritual_notification.IRitualNotificationListener;
import com.aerofs.ritual_notification.RitualNotificationClient;
import com.aerofs.ritual_notification.RitualNotificationSystemConfiguration;
import com.aerofs.ui.UIUtil;
import org.apache.commons.cli.CommandLine;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CmdTransfers extends AbstractShellCommand<ShProgram>
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
        RitualNotificationClient rnc = new RitualNotificationClient(new RitualNotificationSystemConfiguration());
        rnc.addListener(new IRitualNotificationListener() {
            @Override
            public void onNotificationReceived(PBNotification pb)
            {
                print(pb, s.out(), debugFinal);
            }

            @Override
            public void onNotificationChannelBroken()
            {
                // noop // FIXME (AG): (WTF? Really)
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

    public void print(PBNotification pb, PrintStream ps, boolean debug)
    {
        if (pb.getType() == Type.TRANSFER) {
            printTransfer(pb.getTransfer(), ps, debug);
        }
    }

    private void printTime(PrintStream ps)
    {
        ps.print(new SimpleDateFormat().format(new Date()) + " | ");
    }

    private void printTransfer(PBTransferEvent ev, PrintStream ps, boolean debug)
    {
        printTime(ps);
        ps.print(ev.getUpload() ? "UL | " : "DL | ");

        printProgress(ps, ev);

        // print device id and sockid
        if (debug) {
            ps.print(new DID(ev.getDeviceId()).toStringFormal() + "\t| ");
            ps.print(new SOCID(ev.getSocid()) + "\t| ");
        }

        // print path
        String str;
        if (debug) {
            str = Path.fromPB(ev.getPath()).toString();
        } else {
            Path path = Path.fromPB(ev.getPath());
            str = UIUtil.getUserFriendlyPath(ev.getSocid(), ev.getPath(), path);
        }
        ps.println(str);
    }

    private void printProgress(PrintStream ps, PBTransferEvent ev)
    {
        if (ev.getDone() == ev.getTotal()) {
            // N.B. the width of the string is specifically chosen to center and to
            //   line up with the other case
            ps.format("%s | ", ev.getFailed() ? "Failed" : " Done ");
        } else {
            // N.B. the number of white space is specifically chosen to line up with the
            //   output from the other case and to horizontally center the output.
            long percentage = ev.getTotal() == 0 ? 0 : ev.getDone() * 100 / ev.getTotal();
            ps.format("  %2d%%  | ", percentage);
        }
    }
}
