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

import com.aerofs.baseline.admin.Command;
import com.aerofs.baseline.admin.HealthCheck;
import com.aerofs.baseline.admin.RegisteredCommands;
import com.aerofs.baseline.admin.RegisteredHealthChecks;
import com.aerofs.baseline.auth.Authenticator;
import com.aerofs.baseline.auth.Authenticators;
import com.aerofs.baseline.config.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.util.Timer;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.Binder;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.jersey.server.ResourceConfig;

import javax.annotation.concurrent.NotThreadSafe;
import javax.validation.Validator;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Used to setup the runtime environment for a baseline {@link Service}.
 * <br>
 * {@link Service} implementations can use this object to:
 * <ul>
 *     <li>Register HK2 injection definitions.</li>
 *     <li>Register admin commands and health checks.</li>
 *     <li>Add components whose lifecycle should be managed by baseline.</li>
 *     <li>Add authenticators for creating SecurityContext instances.</li>
 *     <li>Add JAX-RS providers.</li>
 *     <li>Add JAX-RS root resources.</li>
 * </ul>
 * {@code Service} implementations should only call methods on
 * this object during {@link Service#init(Configuration, Environment)}
 * execution.
 */
@NotThreadSafe
public final class Environment {

    private final ResourceConfig adminResourceConfig = new ResourceConfig();
    private final ResourceConfig serviceResourceConfig = new ResourceConfig();
    private final ServiceLocator rootLocator;
    private final LifecycleManager lifecycleManager;
    private final Validator validator;
    private final ObjectMapper mapper;
    private final Authenticators authenticators;
    private final RegisteredCommands commands;
    private final RegisteredHealthChecks healthChecks;

    Environment(ServiceLocator rootLocator, LifecycleManager lifecycleManager, Validator validator, ObjectMapper mapper, Authenticators authenticators, RegisteredCommands commands, RegisteredHealthChecks healthChecks) {
        this.rootLocator = rootLocator;
        this.lifecycleManager = lifecycleManager;
        this.validator = validator;
        this.mapper = mapper;
        this.authenticators = authenticators;
        this.commands = commands;
        this.healthChecks = healthChecks;
    }

    final ResourceConfig getAdminResourceConfig() {
        return adminResourceConfig;
    }

    final ResourceConfig getServiceResourceConfig() {
        return serviceResourceConfig;
    }

    /**
     * Get the system-wide JSR-303 validator.
     *
     * @return system-wide JSR-303 validator
     */
    public Validator getValidator() {
        return validator;
    }

    /**
     * Get the system-wide Jackson JSON {@code ObjectMapper}.
     *
     * @return system-wide Jackson JSON {@code ObjectMapper}
     */
    public ObjectMapper getMapper() {
        return mapper;
    }

    /**
     * Get the system-wide {@code ExecutorService} used to schedule periodic tasks.
     *
     * @return system-wide {@code ExecutorService} used to schedule periodic tasks
     */
    public ScheduledExecutorService getScheduledExecutorService() {
        return lifecycleManager.getScheduledExecutorService();
    }

    /**
     * Get the system-wide {@code Timer}
     *
     * @return system-wide {@code Timer}
     */
    public Timer getTimer() {
        return lifecycleManager.getTimer();
    }

    /**
     * Get an instance of type {@code T}.
     * <br>
     * An instance of {@code T} will be located using HK2.
     * If no instance currently exists, one is created by the
     * underlying {@code ServiceLocator}, otherwise an existing instance
     * is returned.
     * <br>
     * The lifecycle of this object is <strong>NOT</strong>
     * managed by HK2. It is highly recommended that this method is
     * <strong>ONLY</strong> used to get singleton instances.
     *
     * @param implementationClass type of the instance to be located/created
     * @param <T> type of the instance to be located/created
     * @return valid, non-null instance of {@code implementationClass}
     * @throws IllegalStateException if a valid instance cannot be constructed
     */
    public <T> T getInstance(Class<T> implementationClass) {
        ServiceHandle<T> handle = Injection.getServiceHandle(rootLocator, implementationClass);
        return handle.getService();
    }

    /**
     * Configure the {@code ServiceLocator} wrapped by this
     * {@code Injector} using a caller-specified {@code Binder}.
     *
     * This is injection configuration in explicit mode. When
     * using a {@code binder} <strong>all JSR-330 annotations</strong>
     * are ignored and the caller must specify the binding type and scope
     * explicitly.
     * <br>
     * Examples:
     * <br>
     * To bind a constant:
     * <pre>
     *     injector.configure(new AbstractBinder() {
     *
     *         void configure() {
     *             bind(constant).to(Constant.class).named("constant_name");
     *         }
     *     }
     * </pre>
     * To bind an already-created singleton instance:
     * <pre>
     *     injector.configure(new AbstractBinder() {
     *
     *         void configure() {
     *             bind(instance).to(Instance.class);
     *         }
     *     }
     * </pre>
     * To bind a yet-to-be-created singleton instance:
     * <pre>
     *     injector.configure(new AbstractBinder() {
     *
     *         void configure() {
     *             bind(Instance.class).to(Instance.class).in(Singleton.class);
     *         }
     *     }
     * </pre>
     * To bind a per-lookup instance:
     * <pre>
     *     injector.configure(new AbstractBinder() {
     *
     *         void configure() {
     *             bind(Instance.class).to(Instance.class);
     *         }
     *     }
     * </pre>
     *
     * @param binder caller-specified injection binder
     */
    public void addBinder(Binder binder) {
        ServiceLocatorUtilities.bind(rootLocator, binder);
    }

    public void addAuthenticator(Authenticator authenticator) {
        authenticators.add(authenticator);
    }

    public void addAuthenticator(Class<? extends Authenticator> authenticator) {
        authenticators.add(authenticator);
    }

    public void addManaged(Managed managed) {
        lifecycleManager.add(managed);
    }

    public void addManaged(Class<? extends Managed> managed) {
        lifecycleManager.add(managed);
    }

    public <T extends Command> void registerCommand(String name, Command command) {
        commands.register(name, command);
    }

    public <T extends Command> void registerCommand(String name, Class<T> command) {
        commands.register(name, command);
    }

    public <T extends HealthCheck> void registerHealthCheck(String name, HealthCheck healthCheck) {
        healthChecks.register(name, healthCheck);
    }

    public <T extends HealthCheck> void registerHealthCheck(String name, Class<T> healthCheck) {
        healthChecks.register(name, healthCheck);
    }

    public void addAdminProvider(Object provider) {
        adminResourceConfig.register(provider);
    }

    public void addAdminProvider(Class<?> provider) {
        adminResourceConfig.register(provider);
    }

    public void addServiceProvider(Object provider) {
        serviceResourceConfig.register(provider);
    }

    public void addServiceProvider(Class <?> provider) {
        serviceResourceConfig.register(provider);
    }

    void addAdminResource(Object resource) {
        adminResourceConfig.register(resource);
    }

    void addAdminResource(Class <?> resource) {
        adminResourceConfig.register(resource);
    }

    public void addResource(Object resource) {
        serviceResourceConfig.register(resource);
    }

    public void addResource(Class <?> resource) {
        serviceResourceConfig.register(resource);
    }
}
