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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Singleton;
import javax.validation.ConstraintViolationException;

@ThreadSafe
@Singleton
final class DefaultUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultUncaughtExceptionHandler.class);

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        if (e instanceof JsonProcessingException) {
            LOGGER.error("fail parse configuration file with error \"{}\"", ((JsonProcessingException) e).getOriginalMessage(), e);
            System.exit(Constants.INVALID_CONFIGURATION_EXIT_CODE);
        } else if (e instanceof ConstraintViolationException) {
            LOGGER.error("invalid configuration with violations\"{}\"", e.getMessage(), e);
            System.exit(Constants.INVALID_CONFIGURATION_EXIT_CODE);
        } else {
            LOGGER.error("fail catch exception thd:{}", t.getName(), e);
            System.exit(Constants.UNCAUGHT_EXCEPTION_ERROR_CODE);
        }
    }
}
