package com.aerofs.restless.jersey;

import com.aerofs.base.Loggers;
import com.google.common.base.Preconditions;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;

/**
 * Base class for Jersey filters that abstracts away path matching for easier port of
 * javax.servlet.Filter implementations
 */
public abstract class ServletFilter implements ContainerRequestFilter
{
    private final String _servletPath;

    protected ServletFilter(String path)
    {
        _servletPath = path;
        // Jersey omits the leading slash in the container request path
        Preconditions.checkState(_servletPath.charAt(0) != '/');
    }

    @Override
    public final ContainerRequest filter(ContainerRequest request)
    {
        String path = request.getPath();
        if (path.startsWith(_servletPath)
                && (path.length() == _servletPath.length()
                            || (path.length() > _servletPath.length()
                                        && path.charAt(_servletPath.length()) == '/'))) {
            Loggers.getLogger(ServletFilter.class).info("filtering {} {}", path, this.getClass());
            return doFilter(request);
        }
        return request;
    }

    protected abstract ContainerRequest doFilter(ContainerRequest request);
}
