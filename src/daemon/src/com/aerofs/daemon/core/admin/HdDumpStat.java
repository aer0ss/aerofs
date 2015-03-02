package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.Dumpables;
import com.aerofs.daemon.event.admin.EIDumpStat;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.event.Prio;
import com.aerofs.proto.Diagnostics.PBDumpStat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map.Entry;

public class HdDumpStat extends AbstractHdIMC<EIDumpStat>
{
    private final long _launchTime = System.currentTimeMillis();

    @Override
    protected void handleThrows_(EIDumpStat ev, Prio prio) throws Exception
    {
        PBDumpStat template = ev.template();
        if (template == null) return;

        PBDumpStat.Builder bd = PBDumpStat.newBuilder();

        if (template.hasUpTime()) {
            bd.setUpTime(System.currentTimeMillis() - _launchTime);
        }

        if (template.hasMisc()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(bos);
            String indentUnit = "  ";

            for (Entry<String, IDumpStatMisc> e : Dumpables.ALL.entrySet()) {
                ps.println(e.getKey());
                e.getValue().dumpStatMisc(indentUnit, indentUnit, ps);
            }

            bd.setMisc(bos.toString());
        }

        ev.setResult_(bd.build());
    }
}
