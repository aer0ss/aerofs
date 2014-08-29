/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base;

import com.google.common.base.Preconditions;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.ThreadNameDeterminer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TimerUtil
{
    private TimerUtil() {}

    private static final AtomicInteger THREAD_ID_COUNTER = new AtomicInteger(0);

    private static AtomicInitializer<Timer> GLOBAL_TIMER = new AtomicInitializer<Timer>() {
        @Override
        protected @Nonnull Timer create()
        {
            ThreadNameDeterminer determiner = (currentThreadName, proposedThreadName) ->
                    "tm" + THREAD_ID_COUNTER.getAndIncrement();

            // 512 comes from looking at netty defaults
            return new HashedWheelTimer(Executors.defaultThreadFactory(), determiner, 200,
                    TimeUnit.MILLISECONDS, 512) {
                @Override
                public Set<Timeout> stop() {
                    // a Timer cannot be restarted after being stopped
                    // we must make sure no single component can stop the global shared timer
                    // as it would break all other components that rely on it. If a component
                    // legitimately needs to stop the Timer it uses then it should use a
                    // private Timer.
                    Preconditions.checkState(false, "The global timer shall not be stopped");
                    return Collections.emptySet();
                }
            };
        }
    };

    /**
     * A HashedWheelTimer to rule them all
     *
     * HashedWheelTimer uses a thread internally so it is highly recommended to create
     * as few instances of it as possible, hence the availability of this global method.
     */
    public static Timer getGlobalTimer()
    {
        return GLOBAL_TIMER.get();
    }
}
