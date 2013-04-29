package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.core.net.UploadState;
import com.aerofs.daemon.core.net.device.DevicePresence;
import com.aerofs.daemon.core.protocol.DownloadState;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.admin.EIDumpStat;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.proto.Files.PBDumpStat;
import com.google.inject.Inject;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class HdDumpStat extends AbstractHdIMC<EIDumpStat>
{
    private final long _launchTime = System.currentTimeMillis();

    private final Transports _tps;
    private final TC _tc;
    private final DirectoryService _ds;
    private final DownloadState _dlstate;
    private final UploadState _ulstate;
    private final CoreQueue _q;
    private final DevicePresence _dp;
    private final TokenManager _tokenManager;

    @Inject
    public HdDumpStat(DevicePresence dp, CoreQueue q, UploadState ulstate, DownloadState dlstate,
            DirectoryService ds, TC tc, Transports tps, TokenManager tokenManager)
    {
        _dp = dp;
        _q = q;
        _ulstate = ulstate;
        _dlstate = dlstate;
        _ds = ds;
        _tc = tc;
        _tps = tps;
        _tokenManager = tokenManager;
    }

    @Override
    protected void handleThrows_(EIDumpStat ev, Prio prio) throws Exception
    {
        PBDumpStat template = ev.template();
        if (template == null) return;

        PBDumpStat.Builder bd = PBDumpStat.newBuilder();

        if (template.hasUpTime()) {
            bd.setUpTime(System.currentTimeMillis() - _launchTime);
        }

        if (template.getTransportCount() != 0) {
            _tps.dumpStat(template, bd);
        }

        if (template.hasMisc()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(bos);
            String indent = "";
            String indentUnit = "  ";
            String indent2 = indentUnit;

            ps.println(indent + "tc");
            _tc.dumpStatMisc(indent2, indentUnit, ps);

            ps.println(indent + "ds");
            _ds.dumpStatMisc(indent2, indentUnit, ps);

            ps.println(indent + "cat");
            _tokenManager.dumpStatMisc(indent2, indentUnit, ps);

            ps.println(indent + "dls");
            _dlstate.dumpStatMisc(indent2, indentUnit, ps);

            ps.println(indent + "uls");
            _ulstate.dumpStatMisc(indent2, indentUnit, ps);

            ps.println(indent + "q");
            _q.dumpStatMisc(indent2, indentUnit, ps);

            ps.println(indent + "dp");
            _dp.dumpStatMisc(indent2, indentUnit, ps);

            // TODO:
            //ps.println(indent + "ssq");
            //_ssq.dumpStatMisc(indent2, indentUnit, ps);

            ps.println(indent + "tps");
            _tps.dumpStatMisc(indent2, indentUnit, ps);

            bd.setMisc(bos.toString());
        }

        ev.setResult_(bd.build());
    }
}
