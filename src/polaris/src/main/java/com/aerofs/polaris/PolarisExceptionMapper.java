package com.aerofs.polaris;

import com.aerofs.base.Loggers;
import com.aerofs.baseline.errors.BaseExceptionMapper;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.logical.*;
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
import java.util.Map;

@Provider
public final class PolarisExceptionMapper extends BaseExceptionMapper<PolarisException> {

    private static final Logger l = Loggers.getLogger(PolarisExceptionMapper.class);

    private final ResourceInfo resourceInfo;

    public PolarisExceptionMapper(@Context ResourceInfo resourceInfo) {
        super(ErrorResponseEntity.NO_STACK_IN_RESPONSE, StackLogging.DISABLE_LOGGING);
        this.resourceInfo = resourceInfo;
    }

    @Override
    protected int getErrorCode(PolarisException throwable) {
        return throwable.getErrorCode().code();
    }

    @Override
    protected String getErrorName(PolarisException throwable) {
        return throwable.getErrorCode().name();
    }

    @Override
    protected String getErrorText(PolarisException throwable) {
        return throwable.getSimpleMessage();
    }

    @Override
    protected void addErrorFields(PolarisException throwable, Map<String, Object> errorFields) {
        throwable.addErrorFields(errorFields);
    }

    protected Response.Status getHttpResponseStatus(PolarisException throwable) {
        if (throwable instanceof AccessException) {
            return Response.Status.FORBIDDEN;
        } else if (throwable instanceof NameConflictException) {
            return Response.Status.CONFLICT;
        } else if (throwable instanceof VersionConflictException) {
            return Response.Status.CONFLICT;
        } else if (throwable instanceof ParentConflictException) {
            return Response.Status.CONFLICT;
        } else if (throwable instanceof NotFoundException) {
            return Response.Status.NOT_FOUND;
        } else if (throwable instanceof ObjectLockedException) {
            return Response.Status.CONFLICT;
        } else {
            return Response.Status.INTERNAL_SERVER_ERROR;
        }
    }

    private boolean isInstanceOfApiClasses()
    {
        Class<?> resourceClass = resourceInfo.getResourceClass();
        return resourceClass == FoldersResource.class ||
                    resourceClass == FilesResource.class ||
                    resourceClass == ChildrenResource.class;
    }

    @Override
    protected String constructEntity(PolarisException throwable) {
        if(isInstanceOfApiClasses()){
            LinkedHashMap<String, Object> errorFields = Maps.newLinkedHashMap();
            errorFields.put("type", throwable.typeForAPIException());
            try {
                return new ObjectMapper().writeValueAsString(errorFields);
            } catch (JsonProcessingException e) {
                l.debug("Unable to create JSON response entity {}", e);
            }
        }
        return super.constructEntity(throwable);
    }
}
