package com.aerofs.lib.analytics;

import java.util.concurrent.atomic.AtomicInteger;

import com.aerofs.lib.C;
import com.aerofs.lib.DelayedRunner;
import com.aerofs.lib.spsv.SVClient;
import com.aerofs.proto.Sv;

public class Analytics
{

    private final AtomicInteger _saveCount = new AtomicInteger(0);

    private final DelayedRunner _dr = new DelayedRunner("analytics-save-file", 60*C.SEC, new Runnable() {
        @Override
        public void run() {
            SVClient.sendEventAsync(Sv.PBSVEvent.Type.FILE_SAVED, Integer.toString(_saveCount.getAndSet(0)));
        }
    });;

    public void incSaveCount()
    {
        _saveCount.incrementAndGet();
        _dr.schedule();
    }
}
