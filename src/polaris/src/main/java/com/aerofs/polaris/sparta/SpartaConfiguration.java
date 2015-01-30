package com.aerofs.polaris.sparta;

import com.google.common.base.Objects;
import org.hibernate.validator.constraints.NotBlank;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;

@SuppressWarnings("unused")
public final class SpartaConfiguration {

    @NotBlank
    private String url;

    @Min(1)
    private long connectTimeout;

    @Min(1)
    private long responseTimeout;

    @Min(1)
    private int maxConnections;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public long getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(long responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SpartaConfiguration other = (SpartaConfiguration) o;

        return Objects.equal(url, other.url)
                && connectTimeout == other.connectTimeout
                && responseTimeout == other.responseTimeout
                && maxConnections == other.maxConnections;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(url, connectTimeout, responseTimeout, maxConnections);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("url", url)
                .add("connectTimeout", connectTimeout)
                .add("responseTimeout", responseTimeout)
                .add("maxConnections", maxConnections)
                .toString();
    }
}
