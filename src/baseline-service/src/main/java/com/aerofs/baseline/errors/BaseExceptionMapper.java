/*
 * Copyright 2015 Air Computing Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aerofs.baseline.errors;

import com.aerofs.baseline.http.ChannelId;
import com.aerofs.baseline.http.Headers;
import com.aerofs.baseline.http.RequestId;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public abstract class BaseExceptionMapper<T extends Throwable> implements ExceptionMapper<T> {

    private static final String UNKNOWN_ID_LOG_VALUE = "UNKNOWN";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public enum ErrorResponseEntity {
        NO_STACK_IN_RESPONSE,
        INCLUDE_STACK_IN_RESPONSE,
    }

    public enum StackLogging {
        ENABLE_LOGGING,
        DISABLE_LOGGING
    }

    private static final Random RANDOM = new Random();

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());
    @Inject protected Provider<ChannelId> channelIdProvider;
    @Inject protected Provider<RequestId> requestIdProvider;

    private final ErrorResponseEntity responseEntity;
    private final StackLogging stackLogging;

    public BaseExceptionMapper(ErrorResponseEntity responseEntity, StackLogging stackLogging) {
        this.responseEntity = responseEntity;
        this.stackLogging = stackLogging;
    }

    @Override
    public final Response toResponse(T throwable) {
        String failureId = Integer.toHexString(RANDOM.nextInt(Integer.MAX_VALUE));

        logFailure(failureId, throwable);

        if (throwable instanceof WebApplicationException) {
            WebApplicationException specific = (WebApplicationException) throwable;
            specific.getResponse().getHeaders().add(Headers.REQUEST_FAILURE_ID_HEADER, failureId);
            return specific.getResponse();
        } else{
            Response.ResponseBuilder builder = Response
                    .status(getHttpResponseStatus(throwable))
                    .header(Headers.REQUEST_FAILURE_ID_HEADER, failureId);

            String entity = constructEntity(throwable);
            if (entity != null) {
                builder.entity(entity);
            }

            return builder.build();
        }
    }

    private void logFailure(String failureId, T throwable) {
        switch (stackLogging) {
            case ENABLE_LOGGING:
                LOGGER.warn("{}: [{}] exception-mapping failed request fid:{}", getChannelId(), getRequestId(), failureId, throwable);
                break;
            default:
                LOGGER.warn("{}: [{}] exception-mapping failed request fid:{}", getChannelId(), getRequestId(), failureId);
                break;
        }
    }

    private String getChannelId() {
        ChannelId id = channelIdProvider.get();
        return id == null ? UNKNOWN_ID_LOG_VALUE : id.getValue();
    }

    private @Nullable String getRequestId() {
        RequestId id = requestIdProvider.get();
        return id == null ? UNKNOWN_ID_LOG_VALUE : id.getValue();
    }

    @Nullable
    protected String constructEntity(T throwable) {
        LinkedHashMap<String, Object> errorFields = Maps.newLinkedHashMap();

        int errorCode = getErrorCode(throwable);
        String errorName = getErrorName(throwable);
        String errorType = throwable.getClass().getSimpleName();
        String errorText = getErrorText(throwable);

        errorFields.put("error_code", errorCode);
        errorFields.put("error_name", errorName);
        errorFields.put("error_type", errorType);

        addErrorFields(throwable, errorFields);

        if (errorText != null) {
            errorFields.put("error_text", errorText);
        }

        if (responseEntity == ErrorResponseEntity.INCLUDE_STACK_IN_RESPONSE) {
            errorFields.put("error_trace", Throwables.getStackTraceAsString(throwable));
        }

        try {
            return MAPPER.writeValueAsString(errorFields);
        } catch (JsonProcessingException e) {
            return String.format("{\"error_code\":\"%d\",\"error_name\":\"%s\",\"error_type\":\"%s\",\"error_text\":\"%s\",\"error_trace\":\"%s\"}", errorCode, errorName, throwable.getClass().getSimpleName(), errorText, Throwables.getStackTraceAsString(throwable));
        }
    }

    @SuppressWarnings("unused")
    protected void addErrorFields(T throwable, Map<String, Object> errorFields) {
        // noop
    }

    protected abstract int getErrorCode(T throwable);

    protected abstract String getErrorName(T throwable);

    protected abstract String getErrorText(T throwable);

    protected abstract Response.Status getHttpResponseStatus(T throwable);
}
