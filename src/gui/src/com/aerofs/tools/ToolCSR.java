package com.aerofs.tools;

import com.aerofs.lib.OpenSslPkcs10;

import com.aerofs.lib.SecUtil;
import com.aerofs.lib.cfg.Cfg;

public class ToolCSR implements ITool {

    @Override
    public void run(String[] args) throws Exception
    {
        Cfg.init_(Cfg.absRTRoot(), true);

        OpenSslPkcs10 csr = SecUtil.newCSR(Cfg.cert().getPublicKey(), Cfg.privateKey(),
                Cfg.user(), Cfg.did());
        csr.print(System.out);
    }

    @Override
    public String getName()
    {
        return "csr";
    }
}
