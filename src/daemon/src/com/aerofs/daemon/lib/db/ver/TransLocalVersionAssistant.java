package com.aerofs.daemon.lib.db.ver;

import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.ver.VersionAssistant.Factory;
import com.google.inject.Inject;

public class TransLocalVersionAssistant extends TransLocal<VersionAssistant>
{
    private final Factory _factVA;

    @Inject
    public TransLocalVersionAssistant(VersionAssistant.Factory factVA)
    {
        _factVA = factVA;
    }

    @Override
    protected VersionAssistant initialValue(Trans t)
    {
        VersionAssistant va = _factVA.create_();
        t.addListener_(va);
        return va;
    }
}
