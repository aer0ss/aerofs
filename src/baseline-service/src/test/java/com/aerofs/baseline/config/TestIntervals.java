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

import org.junit.Test;

import javax.validation.ValidationException;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class TestIntervals
{
    @Test
    public void shouldTreatPlainIntegerAsMillis() {
        assertThat(Intervals.parseFrom("100"), equalTo(100L));
    }

    @Test
    public void shouldConvertSecondsToMillis() {
        assertThat(Intervals.parseFrom("30s"), equalTo(MILLISECONDS.convert(30, SECONDS)));
    }

    @Test
    public void shouldConvertMinutesToMillis() {
        assertThat(Intervals.parseFrom("90m"), equalTo(MILLISECONDS.convert(90, MINUTES)));
    }

    @Test
    public void shouldConvertHoursToMillis() {
        assertThat(Intervals.parseFrom("1h"), equalTo(MILLISECONDS.convert(1, HOURS)));
    }

    @Test
    public void shouldConvertDaysToMillis() {
        assertThat(Intervals.parseFrom("3d"), equalTo(MILLISECONDS.convert(3, DAYS)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotAllowEmptyString() {
        Intervals.parseFrom("");
    }

    @Test(expected = ValidationException.class)
    public void shouldNotAllowInvalidPattern0() {
        Intervals.parseFrom("h");
    }

    @Test(expected = ValidationException.class)
    public void shouldNotAllowInvalidPattern1() {
        Intervals.parseFrom("shm");
    }

    @Test(expected = ValidationException.class)
    public void shouldNotAllowInvalidPattern2() {
        Intervals.parseFrom("8shm");
    }
}