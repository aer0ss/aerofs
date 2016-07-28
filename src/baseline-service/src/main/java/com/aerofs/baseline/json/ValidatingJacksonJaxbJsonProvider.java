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

package com.aerofs.baseline.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.cfg.Annotations;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.google.common.base.Preconditions;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Singleton;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

@ThreadSafe
@Singleton
public final class ValidatingJacksonJaxbJsonProvider extends JacksonJaxbJsonProvider {

    private final Validator validator;

    public ValidatingJacksonJaxbJsonProvider(Validator validator, ObjectMapper objectMapper, Annotations[] defaultAnnotations) {
        super(objectMapper, defaultAnnotations);
        this.validator = validator;
    }

    @Override
    public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException {
        Object deserialized = super.readFrom(type, genericType, annotations, mediaType, httpHeaders, entityStream);
        Preconditions.checkArgument(deserialized != null, "empty JSON body not allowed");

        Set<ConstraintViolation<Object>> violations = validator.validate(deserialized);
        if (violations != null && !violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        return deserialized;
    }

    @Override
    public void writeTo(Object value, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException {
        Set<ConstraintViolation<Object>> violations = validator.validate(value);
        if (violations != null && !violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        super.writeTo(value, type, genericType, annotations, mediaType, httpHeaders, entityStream);
    }
}
