package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.event.net.EOTransportFloodQuery;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.transport.lib.TransportDiagnosisState.FloodEntry;
import com.aerofs.lib.ex.ExNotFound;

public class HdTransportFloodQuery extends AbstractHdIMC<EOTransportFloodQuery>
{

    private final TransportDiagnosisState _tds;

    public HdTransportFloodQuery(ITransportImpl tp)
    {
        _tds = tp.tds();
    }

    @Override
    protected void handleThrows_(EOTransportFloodQuery ev, Prio prio) throws Exception
    {
        FloodEntry fe = _tds.getFlood(ev._seq);
        if (fe == null) throw new ExNotFound();
        ev.setResult_(fe._time, fe._bytes);
    }
}
