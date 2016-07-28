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

import com.aerofs.baseline.logging.ConsoleLoggingConfiguration;
import com.aerofs.baseline.logging.FileLoggingConfiguration;
import com.aerofs.baseline.logging.Logging;
import com.aerofs.baseline.logging.LoggingConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.ServiceLocatorFactory;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

@SuppressWarnings("unchecked")
public final class TestLifecycleManager {

    //
    // types used in the tests below
    //

    private static final class Injected {

        private final List<Class<? extends Managed>> orderedManaged = Lists.newArrayList();

        void add(Managed managed) {
            orderedManaged.add(managed.getClass());
        }

        List<Class<? extends Managed>> get() {
            return ImmutableList.copyOf(orderedManaged);
        }
    }

    private  static final class Managed0 implements Managed {

        private final Injected injected;

        @Inject
        public Managed0(Injected injected) {
            this.injected = injected;
        }

        @Override
        public void start() throws Exception {
            this.injected.add(this);
        }

        @Override
        public void stop() {
            // noop
        }
    }

    private static final class Managed1 implements Managed {

        private final Injected injected;

        @Inject
        public Managed1(Injected injected) {
            this.injected = injected;
        }

        @Override
        public void start() throws Exception {
            this.injected.add(this);
        }

        @Override
        public void stop() {
            // noop
        }
    }

    //
    // tests
    //

    private final ServiceLocator locator = ServiceLocatorFactory.getInstance().create("test");
    private final LifecycleManager lifecycleManager = new LifecycleManager(locator);

    @Before
    public void setup() {
        ConsoleLoggingConfiguration console = new ConsoleLoggingConfiguration();
        console.setEnabled(true);

        FileLoggingConfiguration file = new FileLoggingConfiguration();
        file.setEnabled(false);

        LoggingConfiguration logging = new LoggingConfiguration();
        logging.setLevel("ALL");
        logging.setConsole(console);
        logging.setFile(file);

        Logging.setupLogging(logging);
    }

    @After
    public void teardown() {
        lifecycleManager.stop();
        locator.shutdown();
    }

    @Test
    public void shouldStartManagedInstances() throws Exception {
        Injected shared = new Injected();
        Managed0 managed0 = new Managed0(shared);
        Managed1 managed1 = new Managed1(shared);

        lifecycleManager.add(managed0);
        lifecycleManager.add(managed1);

        lifecycleManager.start();

        assertThat(shared.get(), contains(Managed0.class, Managed1.class)); // started in order
    }

    @Test
    public void shouldStartManagedImplementationClasses() throws Exception {
        ServiceLocatorUtilities.bind(locator, new AbstractBinder() {

            @Override
            protected void configure() {
                bind(Injected.class).to(Injected.class).in(Singleton.class);
                bind(Managed0.class).to(Managed0.class).in(Singleton.class);
                bind(Managed1.class).to(Managed1.class).in(Singleton.class);
            }
        });

        lifecycleManager.add(Managed0.class);
        lifecycleManager.add(Managed1.class);

        lifecycleManager.start();

        Injected shared = locator.getServiceHandle(Injected.class).getService();
        assertThat(shared.get(), contains(Managed0.class, Managed1.class)); // started in order
    }

    @Test
    public void shouldStartMixOfManagedInstancesAndImplementationClasses() throws Exception {
        Injected shared = new Injected();
        Managed0 managed0 = new Managed0(shared);

        ServiceLocatorUtilities.bind(locator, new AbstractBinder() {

            @Override
            protected void configure() {
                bind(shared).to(Injected.class);
                bind(Managed1.class).to(Managed1.class).in(Singleton.class);
            }
        });

        lifecycleManager.add(managed0);
        lifecycleManager.add(Managed1.class);

        lifecycleManager.start();

        assertThat(shared.get(), contains(Managed0.class, Managed1.class)); // started in order
    }

    @Ignore // FIXME (AG): I'd love to re-enable this, but until I figure out how to get a bind call to be able to mark *instances* as singletons, I can't
    @Test(expected = IllegalStateException.class)
    public void shouldFailStartManagedImplementationClassesIfTheyAreNotSingletons() throws Exception {
        ServiceLocatorUtilities.bind(locator, new AbstractBinder() {

            @Override
            protected void configure() {
                bind(Injected.class).to(Injected.class).in(Singleton.class);
                bind(Managed0.class).to(Managed0.class);
            }
        });

        lifecycleManager.add(Managed0.class);

        lifecycleManager.start();
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailStartManagedImplementationClassesIfTheyCannotBeFound() throws Exception {
        ServiceLocatorUtilities.bind(locator, new AbstractBinder() {

            @Override
            protected void configure() {
                bind(Injected.class).to(Injected.class).in(Singleton.class);
            }
        });

        lifecycleManager.add(Managed0.class);

        lifecycleManager.start();
    }

    //
    // check that the LifecycleManager is properly
    // wired up into the Service
    //

    public static final class InjectedManaged implements Managed {

        private final Semaphore startSemaphore;
        private final Semaphore stopSemaphore;

        @Inject
        InjectedManaged(@Named("start") Semaphore startSemaphore, @Named("stop") Semaphore stopSemaphore) {
            this.startSemaphore = startSemaphore;
            this.stopSemaphore = stopSemaphore;
        }

        @Override
        public void start() throws Exception {
            startSemaphore.release();
        }

        @Override
        public void stop() {
            stopSemaphore.release();
        }
    }

    @Test
    public void shouldStartServerWithManagedServicesSuccessfully() throws Exception {

        Semaphore startSemaphore = new Semaphore(0);
        Semaphore stopSemaphore = new Semaphore(0);

        Service<ServiceConfiguration> service = new Service<ServiceConfiguration>("test") {

            @Override
            public void init(ServiceConfiguration configuration, Environment environment) throws Exception {
                environment.addManaged(InjectedManaged.class);
                environment.addManaged(new Managed() {
                    @Override
                    public void start() throws Exception {
                        startSemaphore.release();
                    }

                    @Override
                    public void stop() {
                        stopSemaphore.release();
                    }
                });
                environment.addBinder(new AbstractBinder() {
                    @Override
                    protected void configure() {
                        bind(startSemaphore).to(Semaphore.class).named("start");
                        bind(stopSemaphore).to(Semaphore.class).named("stop");
                        bind(InjectedManaged.class).to(InjectedManaged.class).in(Singleton.class);
                    }
                });
            }
        };

        try {
            // setup the server
            service.runWithConfiguration(ServiceConfiguration.TEST_CONFIGURATION);

            // verify that both services started up
            startSemaphore.tryAcquire(2, 30, TimeUnit.SECONDS);

            // now, stop the service
            service.shutdown();

            // verify that both services stopped
            stopSemaphore.tryAcquire(2, 30, TimeUnit.SECONDS);
        }  finally {
            service.shutdown(); // just in case
        }
    }
}