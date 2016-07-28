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

package com.aerofs.baseline.config;

import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Singleton;

@SuppressWarnings({"unchecked", "unused"})
@ThreadSafe
@Singleton
public final class ConfigurationBinder<T extends Configuration> extends AbstractBinder {

    private final T configuration;
    private final Class<T> configurationClass;

    public ConfigurationBinder(T configuration, Class<? extends Configuration> configurationClass) {
        this.configuration = configuration;
        this.configurationClass = (Class<T>) configurationClass;
    }

    @Override
    protected void configure() {
        bind(configuration).to(configurationClass);
    }
}
