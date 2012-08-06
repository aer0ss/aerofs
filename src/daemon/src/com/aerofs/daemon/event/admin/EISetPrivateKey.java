package com.aerofs.daemon.event.admin;

import java.security.PrivateKey;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;

public class EISetPrivateKey extends AbstractEBIMC
{

    private final PrivateKey _key;

    public EISetPrivateKey(PrivateKey key)
    {
        super(Core.imce());
        _key = key;
    }

    public PrivateKey key()
    {
        return _key;
    }
}
