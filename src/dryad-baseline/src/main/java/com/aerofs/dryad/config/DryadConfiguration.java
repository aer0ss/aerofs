/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad.config;

import com.aerofs.baseline.config.Configuration;
import com.aerofs.dryad.Constants;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@SuppressWarnings("unused")
public final class DryadConfiguration extends Configuration {

    @NotNull
    private String storageDirectory = Constants.DEFAULT_STORAGE_DIRECTORY;

    @Valid
    @NotNull
    private BlacklistConfiguration blacklist = new BlacklistConfiguration();

    public String getStorageDirectory() {
        return storageDirectory;
    }

    public void setStorageDirectory(String storageDirectory) {
        this.storageDirectory = storageDirectory;
    }

    public BlacklistConfiguration getBlacklist() {
        return blacklist;
    }

    public void setBlacklist(BlacklistConfiguration blacklist) {
        this.blacklist = blacklist;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        DryadConfiguration other = (DryadConfiguration) o;

        return Objects.equal(storageDirectory, other.storageDirectory) && Objects.equal(blacklist, other.blacklist);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), storageDirectory, blacklist);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("app", getService())
                .add("admin", getAdmin())
                .add("logging", getLogging())
                .add("storageDirectory", storageDirectory)
                .add("blacklist", blacklist)
                .toString();
    }
}
