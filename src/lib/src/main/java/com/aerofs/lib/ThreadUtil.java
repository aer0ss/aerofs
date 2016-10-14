/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ThreadUtil
{
    private static final Logger l = LoggerFactory.getLogger(ThreadUtil.class);

    static final int INITIAL_ENUMERATION_ARRAY_SIZE = 30;

    static {
        assert ThreadUtil.INITIAL_ENUMERATION_ARRAY_SIZE > 0;
    }

    private ThreadUtil()
    {
        // private to enforce uninstantiability
    }

    public static void sleepUninterruptable(long ms)
    {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            SystemUtil.fatal(e);
        }
    }

    public static void waitUninterruptable(Object obj, long ms)
    {
        try {
            obj.wait(ms);
        } catch (InterruptedException e) {
            SystemUtil.fatal(e);
        }
    }

    public static void waitUninterruptable(Object obj)
    {
        waitUninterruptable(obj, 0);
    }

    public static Thread startDaemonThread(String name, Runnable run)
    {
        if (l.isDebugEnabled()) {
            l.debug("startDaemonThread: " + name);
        }

        Thread thd = new Thread(run);
        thd.setName(name);
        thd.setDaemon(true);
        thd.start();
        return thd;
    }

    private static ThreadGroup getRootThreadGroup()
    {
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        while (group.getParent() != null) {
            group = group.getParent();
        }

        return group;
    }

    static Thread[] getAllThreads()
    {
        return getAllChildThreads(getRootThreadGroup());
    }

    private static Thread[] getAllChildThreads(ThreadGroup group)
    {
        int enumeratedGroups;
        int arraySize = INITIAL_ENUMERATION_ARRAY_SIZE;
        Thread[] threads;
        do {

            //
            // we have to do this because of the contract provided by ThreadGroup.enumerate
            // basically, we can't know the number of threads a-priori so we can't appropriately
            // size the array. The best we can do is pick a number and use it. If the array is too
            // small ThreadGroup.enumerate will silently drop the remaining threads. The only way
            // to detect this is to check if the number of enumerated threads is _exactly_
            // equal to the size of the array
            //

            arraySize = arraySize * 2;
            threads = new Thread[arraySize];
            enumeratedGroups = group.enumerate(threads, true);
        } while (enumeratedGroups == arraySize);

        return threads;
    }

    public static ThreadFactory threadGroupFactory(ThreadGroup group)
    {
        return threadGroupFactory(group, Integer.MIN_VALUE);
    }

    public static ThreadFactory threadGroupFactory(final ThreadGroup group, final int priority)
    {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(group, r,
                                      group.getName() + '-' + threadNumber.getAndIncrement(),
                                      0);
                if (priority != Integer.MIN_VALUE) t.setPriority(priority);
                return t;
            }
        };
        return factory;
    }
}
