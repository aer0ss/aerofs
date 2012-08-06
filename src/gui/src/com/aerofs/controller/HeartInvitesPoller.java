package com.aerofs.controller;

import com.aerofs.lib.C;
import com.aerofs.lib.Param.SP;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.spsv.SPBlockingClient;
import com.aerofs.lib.spsv.SPClientFactory;

/**
 * This class periodically polls the server to query folderless invites quota
 */
public class HeartInvitesPoller
{
    void start()
    {
        Util.startDaemonThread("heart-poller", new Runnable() {
            @Override
            public void run() {
                while (true) {
                    poll();
                    Util.sleepUninterruptable(12 * C.HOUR);
                }
            }
        });
    }

    private void poll()
    {
        SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());

        try {
            sp.signInRemote();
            int quota = sp.getHeartInvitesQuota().getCount();
            long old = Cfg.db().getInt(Key.FOLDERLESS_INVITES);
            if (quota == old) return;

            Cfg.db().set(Key.FOLDERLESS_INVITES, quota);

        } catch (Exception e) {
            Util.l(this).warn("ignored: " + Util.e(e));
        }
    }
}
