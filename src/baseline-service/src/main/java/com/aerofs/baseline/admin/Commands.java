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

package com.aerofs.baseline.admin;

import com.aerofs.baseline.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.io.Writer;

/**
 * Utility methods for handling common tasks
 * required by {@link Command} implementations.
 */
@ThreadSafe
public abstract class Commands {

    /**
     * Prints pretty-printed or non-pretty-printed JSON to an output stream
     * based on whether a query parameter named {@code pretty} exists.
     *
     * @param mapper {@code ObjectMapper} used to generate the output JSON
     * @param writer {@code OutputStream} to which the output is written
     * @param queryParameters query parameters from the request
     * @param json Jackson-serialized JSON object
     *
     * @throws java.io.IOException if the object cannot be transformed to JSON or written to the output
     */
    public static void outputFormattedJson(ObjectMapper mapper, Writer writer, MultivaluedMap<String, String> queryParameters, @Nullable Object json) throws IOException {
        if (json == null) {
            return;
        }

        if (shouldPrettyPrint(queryParameters)) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(writer, json);
        } else {
            mapper.writeValue(writer, json);
        }
    }

    private static boolean shouldPrettyPrint(MultivaluedMap<String, String> queryParameters) {
        return queryParameters.getFirst(Constants.JSON_COMMAND_RESPONSE_ENTITY_PRETTY_PRINTING_QUERY_PARAMETER) != null;
    }

    private Commands() {
        // to prevent instantiation by subclasses
    }
}
