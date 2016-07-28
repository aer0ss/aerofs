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

package com.aerofs.baseline.db;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.skife.jdbi.v2.DBI;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Implementation of an HK2 {@code Binder} that injects
 * {@link DBI} instances into HK2-managed or
 * {@link javax.ws.rs.core.Context}-annotated
 * JAX-RS objects and methods.
 * <br>
 * An instance of this class can be added either as
 * a JAX-RS {@link javax.ws.rs.ext.Provider} for a Jersey application
 * or to an HK2 {@link org.glassfish.hk2.api.ServiceLocator}.
 */
@SuppressWarnings("unused")
@ThreadSafe
public final class DBIBinder extends AbstractBinder {

    private final DBI dbi;

    public DBIBinder(DBI dbi) {
        this.dbi = dbi;
    }

    @Override
    protected void configure() {
        bind(dbi).to(DBI.class);
    }
}
