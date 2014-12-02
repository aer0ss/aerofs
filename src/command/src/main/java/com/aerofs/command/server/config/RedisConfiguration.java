package com.aerofs.command.server.config;

import com.google.common.base.Objects;
import org.hibernate.validator.constraints.NotEmpty;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;

@SuppressWarnings("unused")
public final class RedisConfiguration {

    @NotEmpty
    private String host;

    @Min(0)
    private short port;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public short getPort() {
        return port;
    }

    public void setPort(short port) {
        this.port = port;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RedisConfiguration other = (RedisConfiguration) o;
        return Objects.equal(host, other.host) && port == other.port;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(host, port);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("host", host)
                .add("port", port)
                .toString();
    }
}
