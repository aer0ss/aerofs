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

import com.google.common.base.Preconditions;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;

/**
 * Utility methods for HK2 injection.
 */
public abstract class Injection {

    /**
     * Get the {@link ServiceHandle} to an instance of type {@code T}.
     *
     * @param locator {@code ServiceLocator} instance used to locate the singleton instance of type {@code T}
     * @param implementationClass type of the service to be located
     * @param <T> type-parameter identifying the type of the service to be located
     * @return valid {@code ServiceHandle} that points to a <strong>active</strong> instance of type {@code T}
     * @throws IllegalStateException if no instance satisfying {@code T} could be found
     */
    public static <T> ServiceHandle<T> getServiceHandle(ServiceLocator locator, Class<T> implementationClass) {
        ServiceHandle<T> handle = locator.getServiceHandle(implementationClass);

        Preconditions.checkState(handle != null, "fail locate type %s", implementationClass.getSimpleName());
        Preconditions.checkState(handle.getService() != null, "fail create instance of %s", implementationClass.getSimpleName());
        Preconditions.checkState(handle.isActive(), "%s is no longer active", implementationClass.getSimpleName());

        return handle;
    }

    private Injection() {
        // to prevent instantiation by subclasses
    }
}
