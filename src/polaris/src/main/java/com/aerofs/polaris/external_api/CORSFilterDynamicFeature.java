package com.aerofs.polaris.external_api;

import com.aerofs.polaris.external_api.rest.util.Since;

import javax.ws.rs.container.DynamicFeature;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.FeatureContext;
import java.lang.reflect.Method;

public class CORSFilterDynamicFeature implements DynamicFeature
{
    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext context)
    {
        Method resourceMethod = resourceInfo.getResourceMethod();
        Since since = resourceMethod.getAnnotation(Since.class);
        // Only public facing API routes have a since annotation i.e. only register CORSFilter
        // for requests made to public facing API routes.
        if (since != null && since.value() != null) {
            context.register(new CORSFilter());
        }
    }
}
