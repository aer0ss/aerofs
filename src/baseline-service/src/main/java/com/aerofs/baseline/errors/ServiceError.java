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

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * JSON object representation of an error returned by baseline.
 */
@SuppressWarnings("unused")
@NotThreadSafe
public final class ServiceError {

    private int errorCode;

    private String errorName;

    private String errorType;

    private String errorText;

    @Nullable
    private String errorTrace;

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorName() {
        return errorName;
    }

    public void setErrorName(String errorName) {
        this.errorName = errorName;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getErrorText() {
        return errorText;
    }

    public void setErrorText(String errorText) {
        this.errorText = errorText;
    }

    @Nullable
    public String getErrorTrace() {
        return errorTrace;
    }

    public void setErrorTrace(@Nullable String errorTrace) {
        this.errorTrace = errorTrace;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ServiceError other = (ServiceError) o;
        return errorCode == other.errorCode
                && Objects.equal(errorName, other.errorName)
                && Objects.equal(errorType, other.errorType)
                && Objects.equal(errorText, other.errorText)
                && Objects.equal(errorTrace, other.errorTrace);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(errorCode, errorName, errorType, errorText, errorTrace);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("errorCode", errorCode)
                .add("errorName", errorName)
                .add("errorType", errorType)
                .add("errorText", errorText)
                .add("errorTrace", errorTrace)
                .toString();
    }
}
