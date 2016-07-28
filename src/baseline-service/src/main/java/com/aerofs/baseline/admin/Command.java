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

import javax.ws.rs.core.MultivaluedMap;
import java.io.PrintWriter;

/**
 * Implemented by a class that performs an admin function
 * on either the service or one or more service components.
 */
public interface Command {

    /**
     * Execute the admin command.
     *
     * @param queryParameters parameters included in the {@code POST} request
     * @param entityWriter output stream to which command output can be written.
     *                     Implementations do not have to close the writer
     *                     after use, though this is good practice. If {@code execute()}
     *                     completes successfully and {@code entityWriter} is closed
     *                     without any data being written to it, a response with
     *                     {@code Content-Length: 0} is returned
     *
     * @throws Exception if the command could not be run successfully.
     * The user is notified via an HTTP error status code that command
     * execution failed. The exact status code, name and text of the
     * response are determined by the chain of registered
     * {@link javax.ws.rs.ext.ExceptionMapper} instances
     */
    void execute(MultivaluedMap<String, String> queryParameters, PrintWriter entityWriter) throws Exception;
}
