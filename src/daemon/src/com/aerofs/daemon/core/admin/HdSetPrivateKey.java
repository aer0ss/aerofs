package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.event.admin.EISetPrivateKey;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;

public class HdSetPrivateKey extends AbstractHdIMC<EISetPrivateKey>
{

    @Override
    protected void handleThrows_(EISetPrivateKey ev, Prio prio) throws Exception
    {
        if (Cfg.privateKey() != null) {
            Util.l(this).warn("private key already set. reset it.");
            //throw new ExNoPerm();
        }
        Cfg.setPrivateKey_(ev.key());
    }

}
