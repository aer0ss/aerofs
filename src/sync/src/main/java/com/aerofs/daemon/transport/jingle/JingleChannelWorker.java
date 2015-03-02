/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.google.common.collect.Queues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ArrayBlockingQueue;

import static com.google.common.base.Preconditions.checkState;

/**
 * Object that executes upstream (i.e. incoming) events on
 * a {@link com.aerofs.daemon.transport.jingle.JingleClientChannel}
 * serially.
 */
final class JingleChannelWorker
{
    private static final Logger l = LoggerFactory.getLogger(JingleChannelWorker.class);

    private final ArrayBlockingQueue<Runnable> channelTasks = Queues.newArrayBlockingQueue(DaemonParam.QUEUE_LENGTH_DEFAULT);
    private final Thread ioThread;

    private volatile boolean running;

    /**
     * Constructor.
     *
     * @param transportId unique id of the transport that owns this {@code JingleChannelWorker} isntance
     */
    JingleChannelWorker(String transportId)
    {
        ioThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                l.info("starting task runner");

                while (running) {
                    try {
                        Runnable channelTask = channelTasks.take();
                        channelTask.run();
                    } catch (InterruptedException e) {
                        l.warn("interrupted during run loop");
                        break;
                    }
                }

                l.info("stopping task runner");
            }
        });
        ioThread.setName(transportId + "-jw");
        ioThread.setDaemon(true);
        ioThread.setUncaughtExceptionHandler(new UncaughtExceptionHandler()
        {
            @Override
            public void uncaughtException(Thread thread, Throwable t)
            {
                l.error("caught unhandled exception while running jingle channel task - terminating", t);
                ExitCode.JINGLE_CHANNEL_TASK_UNCAUGHT_EXCEPTION.exit();
            }
        });
    }

    /**
     * Start this {@code JingleChannelWorker}
     */
    void start() {
        running = true;
        ioThread.start();
    }

    /**
     * Submit a task to be executed by this {@code JingleChannelWorker}.
     *
     * @param channelTask task to be executed
     */
    void submitChannelTask(Runnable channelTask)
    {
        checkState(running);

        try {
            channelTasks.put(channelTask);
        } catch (InterruptedException e) {
            l.warn("interrupted while trying to submit task");
        }
    }

    /**
     * Stop this {@code JingleChannelWorker}
     */
    void stop() {
        running = false;
        ioThread.interrupt();
    }

    public void assertThread()
    {
        checkState(Thread.currentThread() == ioThread);
    }
}
