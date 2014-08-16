package com.aerofs.restless.jersey;

import com.aerofs.restless.Version;
import com.aerofs.restless.Configuration;
import com.aerofs.restless.Since;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.sun.jersey.api.model.AbstractMethod;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import com.sun.jersey.spi.container.ResourceFilter;
import com.sun.jersey.spi.container.ResourceFilterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Map;

public class VersionFilterFactory implements ResourceFilterFactory
{
    public final static String REQUEST_VERSION = "restless-request-version";

    private final static Logger l = LoggerFactory.getLogger(VersionFilterFactory.class);

    private final Configuration _config;
    private final Map<Version, Filter> _filters = Maps.newHashMap();

    @Inject
    public VersionFilterFactory(Configuration config)
    {
        _config = config;
    }

    @Override
    public List<ResourceFilter> create(AbstractMethod am)
    {
        return am.isAnnotationPresent(Since.class)
                ? ImmutableList.<ResourceFilter>of(get(am.getAnnotation(Since.class).value()))
                : null;
    }

    Filter get(String s)
    {
        Version version = Preconditions.checkNotNull(Version.fromStringNullable(s));
        Filter filter = _filters.get(version);
        if (filter == null) {
            filter = new Filter(version);
            _filters.put(version, filter);
        }
        return filter;
    }

    private class Filter implements ResourceFilter, ContainerRequestFilter
    {
        private final Version _version;

        private Filter(Version version)
        {
            _version = version;
        }

        @Override
        public ContainerRequest filter(ContainerRequest request)
        {
            String path = "/" + request.getPath();
            Version minVersion = Version.fromRequestPath(path);
            if (minVersion != null
                    && _config.isSupportedVersion(minVersion)
                    && _version.compareTo(minVersion) <= 0) {
                l.debug("accept version {} [{}]", minVersion, _version);
                request.getProperties().put(REQUEST_VERSION, minVersion);
                return request;
            }
            l.warn("reject version {} [{}]", path, _version);
            throw new WebApplicationException(_config.resourceNotFound(path));
        }

        @Override
        public ContainerRequestFilter getRequestFilter()
        {
            return this;
        }

        @Override
        public ContainerResponseFilter getResponseFilter()
        {
            return null;
        }
    }
}
