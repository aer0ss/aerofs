/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.command.server.config;

import com.google.common.base.Objects;
import org.hibernate.validator.constraints.NotEmpty;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;

@SuppressWarnings("unused")
public final class VerkehrConfiguration {

    @NotEmpty
    private String host;

    @Min(0)
    private short port;

    @NotEmpty
    private String certFile;

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

    public String getCertFile() {
        return certFile;
    }

    public void setCertFile(String certFile) {
        this.certFile = certFile;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VerkehrConfiguration other = (VerkehrConfiguration) o;
        return Objects.equal(host, other.host) && port == other.port && Objects.equal(certFile, other.certFile);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(host, port, certFile);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("host", host)
                .add("port", port)
                .add("certFile", certFile)
                .toString();
    }
}
