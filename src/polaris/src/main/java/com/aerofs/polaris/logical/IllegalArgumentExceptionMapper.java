package com.aerofs.polaris.logical;

import com.aerofs.base.Loggers;
import com.aerofs.baseline.errors.BaseExceptionMapper;
import com.aerofs.baseline.errors.BaselineError;
import com.aerofs.polaris.resources.external_api.ChildrenResource;
import com.aerofs.polaris.resources.external_api.FilesResource;
import com.aerofs.polaris.resources.external_api.FoldersResource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.util.LinkedHashMap;

@Provider
public final class IllegalArgumentExceptionMapper extends BaseExceptionMapper<IllegalArgumentException> {

    private static final Logger l = Loggers.getLogger(IllegalArgumentExceptionMapper.class);

    private final ResourceInfo resourceInfo;

    public IllegalArgumentExceptionMapper(@Context ResourceInfo resourceInfo) {
        super(ErrorResponseEntity.NO_STACK_IN_RESPONSE, StackLogging.DISABLE_LOGGING);
        this.resourceInfo = resourceInfo;
    }

    @Override
    protected int getErrorCode(IllegalArgumentException throwable) {
        return BaselineError.INVALID_INPUT_PARAMETERS.code();
    }

    @Override
    protected String getErrorName(IllegalArgumentException throwable) {
        return BaselineError.INVALID_INPUT_PARAMETERS.name();
    }

    @Override
    protected String getErrorText(IllegalArgumentException throwable) {
        return throwable.getMessage();
    }

    private boolean isInstanceOfApiClasses()
    {
        Class<?> resourceClass = resourceInfo.getResourceClass();
        return resourceClass == FoldersResource.class ||
                resourceClass == FilesResource.class ||
                resourceClass == ChildrenResource.class;
    }

    @Override
    protected String constructEntity(IllegalArgumentException throwable) {
        if(isInstanceOfApiClasses()) {
            LinkedHashMap<String, Object> errorFields = Maps.newLinkedHashMap();
            errorFields.put("type", "BAD_ARGS");
            try {
                    return new ObjectMapper().writeValueAsString(errorFields);
            } catch (JsonProcessingException e) {
                l.warn("Unable to create JSON response entity {}", e);
            }
        }
        return super.constructEntity(throwable);
    }

    @Override
    protected Response.Status getHttpResponseStatus(IllegalArgumentException throwable) {
        return Response.Status.BAD_REQUEST ;
    }
}