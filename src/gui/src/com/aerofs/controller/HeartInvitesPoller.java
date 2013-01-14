package com.aerofs.controller;

import com.aerofs.base.C;
import com.aerofs.base.BaseParam.SP;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.client.SPClientFactory;

/**
 * This class periodically polls the server to query folderless invites quota
 */
public class HeartInvitesPoller
{
    void start()
    {
        ThreadUtil.startDaemonThread("heart-poller", new Runnable()
        {
            @Override
            public void run()
            {
                while (true) {
                    poll();
                    ThreadUtil.sleepUninterruptable(12 * C.HOUR);
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
