package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.core.fs.HdJoinSharedFolder;
import com.aerofs.daemon.core.fs.HdLeaveSharedFolder;
import com.aerofs.daemon.event.admin.EIJoinSharedFolder;
import com.aerofs.daemon.event.admin.EILeaveSharedFolder;
import com.google.inject.Inject;

class SingleuserEventHandlerRegistar implements ICoreEventHandlerRegistrar {
    @Inject HdJoinSharedFolder _hdJoinSharedFolder;
    @Inject HdLeaveSharedFolder _hdLeaveSharedFolder;

    @Override
    public void registerHandlers_(CoreEventDispatcher disp) {
        disp
                .setHandler_(EIJoinSharedFolder.class, _hdJoinSharedFolder)
                .setHandler_(EILeaveSharedFolder.class, _hdLeaveSharedFolder);
    }
}
