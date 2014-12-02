/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.command.server.config;

import com.aerofs.baseline.config.Configuration;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@SuppressWarnings("unused")
public final class CommandServerConfiguration extends Configuration {

    @Valid
    @NotNull
    private RedisConfiguration redis = new RedisConfiguration();

    @Valid
    @NotNull
    private VerkehrConfiguration verkehr = new VerkehrConfiguration();

    public RedisConfiguration getRedis() {
        return redis;
    }

    public void setRedis(RedisConfiguration redis) {
        this.redis = redis;
    }

    public VerkehrConfiguration getVerkehr() {
        return verkehr;
    }

    public void setVerkehr(VerkehrConfiguration verkehr) {
        this.verkehr = verkehr;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        CommandServerConfiguration other = (CommandServerConfiguration) o;
        return Objects.equal(redis, other.redis) && Objects.equal(verkehr, other.verkehr);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), redis, verkehr);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("admin", getAdmin())
                .add("service", getService())
                .add("logging", getLogging())
                .add("redis", redis)
                .add("verkehr", verkehr)
                .toString();
    }
}
