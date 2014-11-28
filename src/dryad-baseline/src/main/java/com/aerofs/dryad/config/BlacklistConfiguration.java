package com.aerofs.dryad.config;

import com.google.common.base.Objects;
import com.google.common.collect.Sets;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.util.Set;

@SuppressWarnings("unused")
public final class BlacklistConfiguration {

    @NotNull
    private Set<String> users = Sets.newHashSet();

    @NotNull
    private Set<String> devices = Sets.newHashSet();

    public Set<String> getUsers() {
        return users;
    }

    public void setUsers(Set<String> users) {
        this.users = users;
    }

    public Set<String> getDevices() {
        return devices;
    }

    public void setDevices(Set<String> devices) {
        this.devices = devices;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BlacklistConfiguration other = (BlacklistConfiguration) o;

        return Objects.equal(users, other.users) && Objects.equal(devices, other.devices);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(users, devices);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("users", users)
                .add("devices", devices)
                .toString();
    }
}
