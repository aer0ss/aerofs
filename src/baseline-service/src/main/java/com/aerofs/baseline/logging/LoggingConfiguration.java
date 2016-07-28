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

package com.aerofs.baseline.logging;

import com.aerofs.baseline.Constants;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import org.hibernate.validator.constraints.NotBlank;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

@SuppressWarnings("unused")
@NotThreadSafe
public final class LoggingConfiguration {

    @NotBlank
    private String level = Constants.DEFAULT_LOG_LEVEL;

    @NotNull
    @Valid
    private ConsoleLoggingConfiguration console = new ConsoleLoggingConfiguration();

    @NotNull
    @Valid
    private FileLoggingConfiguration file = new FileLoggingConfiguration();

    @Valid
    private List<ComponentLoggingConfiguration> components = Lists.newLinkedList();

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public ConsoleLoggingConfiguration getConsole() {
        return console;
    }

    public void setConsole(ConsoleLoggingConfiguration console) {
        this.console = console;
    }

    public FileLoggingConfiguration getFile() {
        return file;
    }

    public void setFile(FileLoggingConfiguration file) {
        this.file = file;
    }

    public List<ComponentLoggingConfiguration> getComponents() {
        return components;
    }

    public void setComponents(List<ComponentLoggingConfiguration> components) {
        this.components = components;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LoggingConfiguration other = (LoggingConfiguration) o;
        return Objects.equal(level, other.level) && Objects.equal(console, other.console) && Objects.equal(file, other.file) && Objects.equal(components, other.components);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(level, console, file, components);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("level", level)
                .add("console", console)
                .add("file", file)
                .add("components", components)
                .toString();
    }
}
