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

package com.aerofs.baseline.auth;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Singleton;
import java.util.List;

/**
 * User-specified list of {@link com.aerofs.baseline.auth.Authenticator}
 * instances that can be used to verify incoming HTTP requests.
 */
@ThreadSafe
public final class Authenticators {

    /**
     * Implemented by classes that authenticate
     * incoming HTTP requests using the authenticators
     * registered with this container.
     */
    interface Visitor {

        /**
         * Visit the {@code Authenticator} instances registered this container
         *
         * @param authenticator instance with which to authenticate the incoming HTTP request
         * @return {@code true} if iteration should continue, {@code false} otherwise
         * @throws Exception if the visiting code could not complete successfully
         */
        boolean visit(Authenticator authenticator) throws Exception;
    }

    private interface AuthenticatorHandle {

        Authenticator get();

        void release();
    }

    private interface AuthenticatorImplementation {

        AuthenticatorHandle getHandle(ServiceLocator locator);
    }

    private final class AuthenticatorInstance implements AuthenticatorImplementation {

        private final AuthenticatorHandle handle;

        private AuthenticatorInstance(Authenticator authenticator) {
            this.handle = new AuthenticatorHandle() {

                @Override
                public Authenticator get() {
                    return authenticator;
                }

                @Override
                public void release() {
                    // noop;
                }
            };
        }

        @Override
        public AuthenticatorHandle getHandle(ServiceLocator locator) {
            return handle;
        }
    }

    private final class AuthenticatorInjected implements AuthenticatorImplementation {

        private final Class<? extends Authenticator> implementationClass;

        private AuthenticatorInjected(Class<? extends Authenticator> implementationClass) {
            this.implementationClass = implementationClass;
        }

        @Override
        public AuthenticatorHandle getHandle(ServiceLocator locator) {
            return new AuthenticatorHandle() {

                private ServiceHandle<? extends Authenticator> serviceHandle;

                @Override
                public Authenticator get() {
                    if (serviceHandle == null) {
                        serviceHandle = locator.getServiceHandle(implementationClass);
                    }

                    Preconditions.checkState(serviceHandle != null, "could not create service handle for %s using %s", implementationClass.getSimpleName(), locator.getName());
                    Preconditions.checkState(serviceHandle.getService() != null, "%s cannot be created using %s", implementationClass.getSimpleName(), locator.getName());
                    Preconditions.checkState(serviceHandle.isActive(), "%s is no longer active within service locator %s", implementationClass.getSimpleName(), locator.getName());

                    return serviceHandle.getService();
                }

                @Override
                public void release() {
                    if (serviceHandle.getActiveDescriptor().getScopeAnnotation() != Singleton.class) {
                        serviceHandle.destroy();
                    }
                }
            };
        }
    }

    private final List<AuthenticatorImplementation> authenticators = Lists.newCopyOnWriteArrayList();

    /**
     * Add an {@code Authenticator} instance.
     *
     * @param authenticator instance used to authenticate incoming HTTP requests
     */
    public void add(Authenticator authenticator) {
        authenticators.add(new AuthenticatorInstance(authenticator));
    }

    /**
     * Add an {@code Authenticator} implementation class.
     * <br>
     * An instance of this class is not created immediately.
     * They are created as necessary using the Jersey {@link ServiceLocator}
     * associated with the request.
     *
     * @param authenticator implementation class to be created to authenticate incoming HTTP requests
     */
    public void add(Class<? extends Authenticator> authenticator) {
        authenticators.add(new AuthenticatorInjected(authenticator));
    }

    /**
     * Iterate over the {@code Authenticator} instances
     * registered with this container.
     *
     * @param locator {@code ServiceLocator} instance used to create/locate any {@link Authenticator} implementation classes
     * @param visitor code to be run against each {@code Authenticator} implementation
     * @throws Exception if the {@code visitor} code fails
     */
    void visit(ServiceLocator locator, Visitor visitor) throws Exception { // FIXME (AG): I don't like that I have to pass in the service locator
        for (AuthenticatorImplementation implementation: authenticators) {

            AuthenticatorHandle handle = null;
            try {
                handle = implementation.getHandle(locator);
                boolean keepVisiting = visitor.visit(handle.get());
                if (!keepVisiting) {
                    break;
                }
            } finally {
                if (handle != null) {
                    handle.release();
                }
            }
        }
    }
}
