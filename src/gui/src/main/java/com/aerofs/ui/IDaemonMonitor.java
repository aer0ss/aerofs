package com.aerofs.ui;

import com.aerofs.LaunchArgs;
import com.aerofs.base.Loggers;
import com.aerofs.lib.cfg.Cfg;
import org.slf4j.Logger;

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
    public class Factory
    {
        private IDaemonMonitor _defaultDaemonMonitor;
        private IDaemonMonitor _noopMonitor;

        public Factory(LaunchArgs launchArgs)
        {
            _defaultDaemonMonitor = new DefaultDaemonMonitor(launchArgs);
            _noopMonitor = new NoopDaemonMonitor();
        }
        /**
         * Creates and returns an implementation of IDaemonMonitor dependant on Cfg.useDM().
         * If Cfg.useDM() returns true, the default daemon monitor will be instantiated. Otherwise,
         * a 'Null' implementation will be instantiated, which effectively does nothing.
         *
         * @return An implementation of IDaemonMonitor
         */
        public IDaemonMonitor get()
        {
            return Cfg.useDM() ? _defaultDaemonMonitor : _noopMonitor;
        }

        public IDaemonMonitor getNoop()
        {
            return _noopMonitor;
        }

        /**
         * A 'Null' implementation of IDaemonMonitor which does nothing.
         */
        private static class NoopDaemonMonitor implements IDaemonMonitor
        {
            private static final Logger l = Loggers.getLogger(NoopDaemonMonitor.class);

            @Override
            public void start() throws Exception
            {
                l.info("starting daemon has no effect");
            }

            @Override
            public void stopIgnoreException()
            {
                stop();
            }

            @Override
            public void stop()
            {
                l.info("stopping daemon has no effect");
            }
        }
    }
}
