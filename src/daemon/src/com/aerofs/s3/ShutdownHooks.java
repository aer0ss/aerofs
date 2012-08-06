package com.aerofs.s3;

import java.util.ArrayList;
import java.util.List;

import com.aerofs.lib.Util;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.log4j.Logger;

@Singleton
public class ShutdownHooks
{
    private static final Logger l = Util.l(ShutdownHooks.class);

    private final List<Runnable> _hooks = new ArrayList<Runnable>();

    @Inject
    public ShutdownHooks()
    {
    }

    public void addShutdownHook_(Runnable r)
    {
        _hooks.add(r);
    }

    public void shutdown_()
    {
        for (int i = _hooks.size(); i-- > 0;) {
            Runnable r = _hooks.get(i);
            try {
                r.run();
            } catch (Exception e) {
                l.error(Util.e(e));
            }
        }
    }
}
