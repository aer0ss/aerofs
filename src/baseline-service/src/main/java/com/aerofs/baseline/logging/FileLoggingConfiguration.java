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
import org.hibernate.validator.constraints.NotBlank;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

@SuppressWarnings("unused")
@NotThreadSafe
public final class FileLoggingConfiguration {

    private boolean enabled = false;

    @NotBlank
    private String logfile = Constants.DEFAULT_LOGFILE_NAME;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLogfile() {
        return logfile;
    }

    public void setLogfile(String logfile) {
        this.logfile = logfile;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileLoggingConfiguration other = (FileLoggingConfiguration) o;
        return enabled == other.enabled && Objects.equal(logfile, other.logfile);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(enabled, logfile);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("enabled", enabled)
                .add("logfile", logfile)
                .toString();
    }
}
