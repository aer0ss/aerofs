package com.aerofs.fsck;

import com.aerofs.lib.IProgram;
import com.aerofs.lib.cfg.CfgModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;


public class FSCKProgram implements IProgram {

    /**
     * args: [-f] output in formal format
     *       [-l] dump db instead of checking for consistency
     *       [-r] repair consistency errors
     */
    @Override
    public void launch_(String rtRoot, String prog, String[] args)
        throws Exception
    {

        Injector injector = Guice.createInjector(Stage.PRODUCTION, new CfgModule(), new FSCKModule());
        FSCK fsck = injector.getInstance(FSCK.class);

        fsck.init_();
        fsck.run_(args);
    }
}
