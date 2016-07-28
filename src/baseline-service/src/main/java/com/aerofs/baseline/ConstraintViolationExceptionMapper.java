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

package com.aerofs.baseline;

import com.aerofs.baseline.errors.BaseExceptionMapper;
import com.aerofs.baseline.errors.BaselineError;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Singleton;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ThreadSafe
@Singleton
public final class ConstraintViolationExceptionMapper extends BaseExceptionMapper<ConstraintViolationException> {

    public ConstraintViolationExceptionMapper() {
        super(ErrorResponseEntity.NO_STACK_IN_RESPONSE, StackLogging.ENABLE_LOGGING);
    }

    @Override
    protected int getErrorCode(ConstraintViolationException throwable) {
        return BaselineError.INVALID_INPUT_PARAMETERS.code();
    }

    @Override
    protected String getErrorName(ConstraintViolationException throwable) {
        return BaselineError.INVALID_INPUT_PARAMETERS.name();
    }

    @Override
    protected String getErrorText(ConstraintViolationException throwable) {
        return "input constraints violated";
    }

    @Override
    protected void addErrorFields(ConstraintViolationException throwable, Map<String, Object> errorFields) {
        List<Map<String, String>> violations = Lists.newArrayList();

        for (Object object : throwable.getConstraintViolations()) {
            ConstraintViolation<?> constraintViolation = ((ConstraintViolation) object);

            LinkedHashMap<String, String> violationProperties = Maps.newLinkedHashMap();
            violationProperties.put("class", constraintViolation.getRootBeanClass().getSimpleName());
            violationProperties.put("property", constraintViolation.getPropertyPath().toString());
            violationProperties.put("cause", constraintViolation.getMessage());

            violations.add(violationProperties);
        }

        errorFields.put("violations", violations);
    }

    @Override
    protected Response.Status getHttpResponseStatus(ConstraintViolationException throwable) {
        return Response.Status.BAD_REQUEST;
    }
}
