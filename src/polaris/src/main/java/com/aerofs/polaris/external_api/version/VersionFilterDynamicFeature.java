package com.aerofs.polaris.external_api.version;


import com.aerofs.polaris.external_api.rest.util.Since;

import javax.ws.rs.container.DynamicFeature;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.FeatureContext;
import java.lang.reflect.Method;

public class VersionFilterDynamicFeature implements DynamicFeature
{
    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext context)
    {
        Method resourceMethod = resourceInfo.getResourceMethod();
        Since since = resourceMethod.getAnnotation(Since.class);
        if (since != null && since.value() != null) {
            context.register(new VersionFilter(since.value()));
        }
    }
}
