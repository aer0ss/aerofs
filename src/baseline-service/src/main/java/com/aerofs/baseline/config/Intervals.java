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

package com.aerofs.baseline.config;

import com.google.common.base.Preconditions;

import javax.annotation.concurrent.ThreadSafe;
import javax.validation.ValidationException;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

@ThreadSafe
abstract class Intervals {

    static long parseFrom(String interval) {
        Preconditions.checkArgument(!interval.isEmpty());

        if (!Pattern.matches("[0-9]+[smhd]?", interval)) {
            throw new ValidationException("bad interval:" + interval);
        }

        long numericInterval;

        char lastChar = interval.charAt(interval.length() - 1);
        if (Character.isDigit(lastChar)) {
            numericInterval = Long.parseLong(interval);
        } else {
            numericInterval = Long.parseLong(interval.substring(0, interval.length() - 1));
        }

        long intervalInMillis;

        switch (lastChar) {
        case 's':
            intervalInMillis = MILLISECONDS.convert(numericInterval, SECONDS);
            break;
        case 'm':
            intervalInMillis = MILLISECONDS.convert(numericInterval, MINUTES);
            break;
        case 'h':
            intervalInMillis = MILLISECONDS.convert(numericInterval, HOURS);
            break;
        case 'd':
            intervalInMillis = MILLISECONDS.convert(numericInterval, DAYS);
            break;
        default:
            intervalInMillis = Long.parseLong(interval);
            break;
        }

        return intervalInMillis;
    }
}
