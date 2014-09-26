package com.aerofs.polaris;

import com.aerofs.polaris.api.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for all polaris exceptions.
 * All exceptions thrown by resources should derive from this class
 * to ensure that a useful HTTP response status code is
 * generated and returned by {@link com.aerofs.polaris.PolarisExceptionMapper}.
 */
public abstract class PolarisException extends Exception {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ErrorCode errorCode;

    public PolarisException(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    protected abstract String getSimpleMessage();

    protected abstract void addProperties(Map<String, Object> errorProperties);

    @Override
    public String toString() {
        return getMessage();
    }

    @Override
    public String getLocalizedMessage() {
        return getMessage();
    }

    @Override
    public String getMessage() {
        LinkedHashMap<String, Object> properties = Maps.newLinkedHashMap();

        properties.put("error_code", errorCode.getCode());
        properties.put("error_code_human_readable", errorCode);
        properties.put("error_message", getSimpleMessage());
        addProperties(properties);

        try {
            return MAPPER.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            return String.format("{\"error_code\":\"%d\"", errorCode.getCode());
        }
    }
}
