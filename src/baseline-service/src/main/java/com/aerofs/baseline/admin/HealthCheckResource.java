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
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;

/**
 * Root resource class for interrogating a baseline
 * service on its overall status.
 * <p>
 * A baseline service may implement zero or more health checks,
 * each of which checks the condition of one or more service
 * components. Each health check returns a {@link Status.Value}
 * of {@code SUCCESS} or {@code FAILURE}. If all implemented checks
 * return {@code SUCCESS} an HTTP response with status code {@code 200}
 * is returned. If <strong>any</strong> health check returns
 * {@code FAILURE} an HTTP response with status code {@code 503} is
 * returned. Additionally, a JSON object of type {@link ServiceStatus}
 * is returned, detailing all the health checks run, their execution
 * status and any additional information.
 * <p>
 * If <strong>no</strong> health checks are implemented an
 * HTTP response with status code {@code 501} and an empty entity
 * is returned.
 * <p>
 * This resource is accessed via:
 * <pre>
 *     curl http://service_url:service_admin_port/status
 * </pre>
 */
@Path(Constants.HEALTH_CHECK_RESOURCE_PATH)
@ThreadSafe
@Singleton
public final class HealthCheckResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckResource.class);

    private final ServiceLocator locator;
    private final RegisteredHealthChecks healthChecks;

    private HealthCheckResource(@Context ServiceLocator locator, @Context RegisteredHealthChecks healthChecks) {
        this.locator = locator;
        this.healthChecks = healthChecks;
    }

    @SuppressWarnings("unchecked")
    @GET
    public Response getStatus() {
        if (!healthChecks.hasHealthChecks()) {
            return Response.status(Response.Status.NOT_IMPLEMENTED).build();
        }

        boolean allSucceeded = true;
        ServiceStatus serviceStatus = new ServiceStatus();

        for (Map.Entry<String, Object> entry: healthChecks.getRegistrations()) {
            String name = entry.getKey();

            try {
                boolean successful;

                if (entry.getValue() instanceof HealthCheck) {
                    successful = executeHealthCheck(name, (HealthCheck) entry.getValue(), serviceStatus);
                } else {
                    successful= loadClassAndExecuteHealthCheck(name, (Class) entry.getValue(), serviceStatus);
                }

                if (!successful) {
                    allSucceeded = false;
                }
            } catch (Exception e) {
                LOGGER.error("failed while executing \"{}\"", name, e);
                allSucceeded = false;
                serviceStatus.addStatus(name, Status.failure(e.getMessage()));
            }
        }

        return Response
                .status(allSucceeded ? Response.Status.OK : Response.Status.SERVICE_UNAVAILABLE)
                .type(MediaType.APPLICATION_JSON)
                .entity(serviceStatus)
                .build();
    }

    private boolean loadClassAndExecuteHealthCheck(String name, Class<? extends HealthCheck> healthCheckClass, ServiceStatus serviceStatus) {
        ServiceHandle<? extends HealthCheck> handle = null;
        try {
            // find and execute the health check
            handle = locator.getServiceHandle(healthCheckClass);
            HealthCheck healthCheck = handle.getService();
            return executeHealthCheck(name, healthCheck, serviceStatus);
        } finally {
            // destroy the created object if it's not a singleton
            if (handle != null && handle.getActiveDescriptor().getScopeAnnotation() != Singleton.class) {
                handle.destroy();
            }
        }
    }

    private boolean executeHealthCheck(String name, HealthCheck healthCheck, ServiceStatus serviceStatus) {
        Status status = healthCheck.check();
        LOGGER.debug("executed \"{}\" with status {}", name, status.getValue());

        serviceStatus.addStatus(name, status);
        return status.getValue() == Status.Value.SUCCESS;
    }
}
