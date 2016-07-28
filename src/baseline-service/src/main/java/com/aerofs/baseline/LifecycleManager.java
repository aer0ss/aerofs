/*
 * Copyright 2015 Air Computing Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aerofs.baseline;

import com.google.common.collect.Lists;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@ThreadSafe
final class LifecycleManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(LifecycleManager.class);

    //
    // wrappers to allow users to register either
    // instances or implementation types that can later
    // be created using HK2
    //

    private interface ManagedWrapper extends Managed {

        // intentionally empty - marker interface
    }

    @NotThreadSafe
    private final class ManagedInstance implements ManagedWrapper {

        private final Managed managed;

        public ManagedInstance(Managed managed) {
            this.managed = managed;
        }

        @Override
        public void start() throws Exception {
            managed.start();
        }

        @Override
        public void stop() {
            managed.stop();
        }
    }

    @NotThreadSafe
    private final class ManagedImplementationClass<T extends Managed> implements ManagedWrapper {

        private final Class<T> type;

        private ServiceHandle<T> handle;

        public ManagedImplementationClass(Class<T> type) {
            this.type = type;
        }

        @Override
        public void start() throws Exception {
            handle = Injection.getServiceHandle(rootLocator, type);
            handle.getService().start();
        }

        @Override
        public void stop() {
            if (handle != null) {
                handle.getService().stop();
                handle.destroy();
                handle = null;
            }
        }
    }

    //
    // LifecycleManager implementation
    //

    private final List<ManagedWrapper> services = Lists.newArrayListWithCapacity(32);
    private final ScheduledThreadPoolExecutor scheduledExecutorService = new ScheduledThreadPoolExecutor(Constants.DEFAULT_SCHEDULED_THREAD_POOL_EXECUTOR_SIZE, Threads.newNamedThreadFactory("sched-thd-%d"));
    private final Timer timer = new HashedWheelTimer();
    private final ServiceLocator rootLocator;

    LifecycleManager(ServiceLocator rootLocator) {
        this.rootLocator = rootLocator;

        add(wrap(new Managed() {
            @Override
            public void start() throws Exception {
                LOGGER.info("start timer");
            }

            @Override
            public void stop() {
                LOGGER.info("stop timer");
                timer.stop();
            }
        }));

        add(wrap(new Managed() {
            @Override
            public void start() throws Exception {
                LOGGER.info("start scheduled executor");
            }

            @Override
            public void stop() {
                LOGGER.info("stop scheduled executor");
                scheduledExecutorService.shutdownNow();
            }
        }));
    }

    Timer getTimer() {
        return timer;
    }

    ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }

    synchronized void add(Managed managed) {
        services.add(wrap(managed)) ;
    }

    synchronized void add(Class<? extends Managed> managed) {
        services.add(wrap(managed)) ;
    }

    private LifecycleManager.ManagedInstance wrap(Managed managed) {
        return new ManagedInstance(managed);
    }

    private ManagedImplementationClass<? extends Managed> wrap(Class<? extends Managed> managed) {
        return new ManagedImplementationClass<>(managed);
    }

    synchronized void start() throws Exception {
        for (Managed managed : services) {
            try {
                managed.start();
            } catch (Exception e) {
                LOGGER.warn("fail start managed:{}", managed.getClass().getSimpleName(), e);
                throw e;
            }
        }
    }

    synchronized void stop() {
        for (Managed managed : Lists.reverse(services)) {
            try {
                managed.stop();
            } catch (Exception e) {
                LOGGER.warn("fail stop managed:{}", managed.getClass().getSimpleName(), e);
            }
        }
    }
}
