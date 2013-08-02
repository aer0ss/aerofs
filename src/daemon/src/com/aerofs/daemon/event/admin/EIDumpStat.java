package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.proto.Diagnostics.PBDumpStat;

public class EIDumpStat extends AbstractEBIMC
{

    private final PBDumpStat _template;
    private PBDumpStat _data;

    public EIDumpStat(PBDumpStat template, IIMCExecutor imce)
    {
        super(imce);
        _template = template;
    }

    public void setResult_(PBDumpStat data)
    {
        _data = data;
    }

    public PBDumpStat template()
    {
        return _template;
    }

    public PBDumpStat data_()
    {
        return _data;
    }
}
