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

package com.aerofs.baseline.admin;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Map;

/**
 * Stores a map of {@code command_name -> command}
 * defined for this service.
 */
@ThreadSafe
public final class RegisteredCommands {

    private final Map<String, Object> commands = Maps.newConcurrentMap();

    /**
     * Get either a command instance or
     * implementation class corresponding to {@code name}.
     *
     * @param name unique name of the command to locate
     * @return {@code null} if {@code name} was not registered
     * with this service; a valid command instance or implementation
     * class corresponding to {@code name} otherwise.
     */
    @Nullable Object get(String name) {
        return commands.get(name);
    }

    /**
     * Add a command named {@code name} with implementation instance {@code command}.
     *
     * @param name unique name identifying this command
     * @param command instance to be executed
     */
    public void register(String name, Command command) {
        registerInternal(name, command);
    }

    /**
     * Add a command named {@code name} with implementation class {@code command}.
     *
     * @param name unique name identifying this command
     * @param command implementation class to be located, loaded and executed.
     *                This class does <strong>NOT</strong> handle locating
     *                or loading the implementation class.
     */
    public void register(String name, Class<? extends Command> command) {
        registerInternal(name, command);
    }

    private void registerInternal(String name, Object command) {
        Object previous = commands.putIfAbsent(name, command);
        Preconditions.checkState(previous == null, "previously registered command \"%s\"", name);
    }
}
