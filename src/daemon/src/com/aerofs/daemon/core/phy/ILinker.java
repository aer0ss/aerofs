/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy;

import com.aerofs.ids.SID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.IStartable;
import com.google.inject.Inject;

import java.io.IOException;
import java.sql.SQLException;

public interface ILinker extends IStartable
{
    public void init_();

    public void restoreRoot_(SID sid, String absPath, Trans t)
            throws SQLException, IOException;

    public void scan_(ScanCompletionCallback callback);

    boolean isFirstScanInProgress_(SID sid);

    public static class NullLinker implements ILinker
    {
        private final CoreScheduler _sched;

        @Inject
        public NullLinker(CoreScheduler sched)
        {
            _sched = sched;
        }

        @Override
        public void init_() {}

        @Override
        public void restoreRoot_(SID sid, String absPath, Trans t)
                throws SQLException, IOException {}

        @Override
        public void start_() {}

        @Override
        public boolean isFirstScanInProgress_(SID sid) { return false; }

        @Override
        public void scan_(final ScanCompletionCallback callback)
        {
            _sched.schedule_(new AbstractEBSelfHandling() {
                @Override
                public void handle_()
                {
                    callback.done_();
                }
            });
        }
    }
}
