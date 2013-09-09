/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.zephyr.server.ZephyrServer;
import com.aerofs.zephyr.server.core.Dispatcher;
import org.junit.rules.ExternalResource;

public class ZephyrResource extends ExternalResource
{
    private Dispatcher _dispatcher = new Dispatcher();
    private ZephyrServer _zephyrServer = new ZephyrServer("localhost", (short) 9999, _dispatcher);

    @Override
    protected void before()
            throws Throwable
    {
        super.before();

        _dispatcher.init();
        _zephyrServer.init();
        _dispatcher.run();
    }

    @Override
    protected void after()
    {
        _zephyrServer.terminate();
        _dispatcher.stop();

        super.after();
    }
}
