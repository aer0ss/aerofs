/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core;

import com.aerofs.base.BaseParam.SP;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.linker.scanner.ScanCompletionCallback;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.client.SPClientFactory;
import com.google.common.collect.Sets;
import org.apache.log4j.Logger;

import java.sql.SQLException;
import java.util.Set;

/**
 * This class is in charge of all logic that needs to be run only once: on the first launch of the
 * daemon after a fresh install.
 *
 * In particular it is in charge of triggering a first scan that will restore shared folders if any
 * valid tag files are found under the root anchor and the SID they point to is accessible to the
 * local user.
 */
public class FirstLaunch
{
    private static final Logger l = Util.l(FirstLaunch.class);

    private final Set<SID> _accessibleStores = Sets.newHashSet();

    public boolean isFirstLaunch_()
    {
        return Cfg.db().getBoolean(Key.FIRST_START);
    }

    public Set<SID> getAccessibleStores_()
    {
        return _accessibleStores;
    }

    /**
     * Perform any first-launch-specific task
     * @param callback a scan completion callback that finalizes Core startup
     * @return a scan completion callback wrapping the given callback
     *
     * NB: ideally we'd just do the scan directly from here but injection makes it impossible
     * because it introduces circular dependencies...
     */
    ScanCompletionCallback onFirstLaunch_(final ScanCompletionCallback callback)
    {
        fetchAccessibleStores_();

        l.info("first-launch: accessible stores " + _accessibleStores);

        return new ScanCompletionCallback() {
            @Override
            public void done_()
            {
                _accessibleStores.clear();

                callback.done_();

                try {
                    // reset FIRST_START flag last, as it is used to reject Ritual calls
                    Cfg.db().set(Key.FIRST_START, false);
                } catch (SQLException e) {
                    SystemUtil.fatal(e);
                }
            }
        };
    }

    private void fetchAccessibleStores_()
    {
        try {
            SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
            sp.signInRemote();
            for (PBStoreACL sacl : sp.getACL(0L).getStoreAclList()) {
                _accessibleStores.add(new SID(sacl.getStoreId()));
            }
        } catch (Exception e) {
            SystemUtil.fatal(e);
        }
    }
}
