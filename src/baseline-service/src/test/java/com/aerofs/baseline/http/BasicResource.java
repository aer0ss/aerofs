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

package com.aerofs.baseline.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;

@Path("/" + Resources.BASIC_RESOURCE)
@Singleton
public final class BasicResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(BasicResource.class);

    @GET
    public String get() {
        LOGGER.info("basic get");
        return "success";
    }

    @Path("/{component}")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    public String printString(@PathParam("component") String component, String body) {
        LOGGER.info("basic post");
        return component + "-" + body;
    }

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    public void returnNothing(@PathParam("component") String component, String body) {
        LOGGER.info("basic post - empty response");
    }
}
