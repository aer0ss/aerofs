package com.aerofs.ui;

import com.aerofs.lib.cfg.Cfg;

import java.io.IOException;

public interface IDaemonMonitor
{
    /**
     * Starts the daemon process and waits until it has successfully started
     * (when it responds to RPC calls).
     *
     * @throws Exception
     */
    void start() throws Exception;

    /**
     * Stops the daemon and ignores any exceptions that may have
     * been thrown in the process.
     */
    void stopIgnoreException();

    /**
     * Only returns once the daemon has successfully stopped running.
     *
     * @throws IOException when the daemon failed to be stopped (daemon might not be running)
     */
    void stop() throws IOException;

    /**
     * Factory class that instantiates an implementation of IDaemonMonitor
     */
    public static abstract class Factory
    {
        /**
         * Creates and returns an implementation of IDaemonMonitor dependant on Cfg.useDM().
         * If Cfg.useDM() returns true, the default daemon monitor will be instantiated. Otherwise,
         * a 'Null' implementation will be instantiated, which effectively does nothing.
         *
         * @return An implementation of IDaemonMonitor
         */
        public static IDaemonMonitor create()
        {
            return Cfg.useDM() ? new DefaultDaemonMonitor() : new NoopDaemonMonitor();
        }

        /**
         * A 'Null' implementation of IDaemonMonitor which does nothing.
         */
        private static class NoopDaemonMonitor implements IDaemonMonitor
        {
            @Override
            public void start() throws Exception
            {
            }

            @Override
            public void stopIgnoreException()
            {
            }

            @Override
            public void stop() throws IOException
            {
            }
        }
    }
}