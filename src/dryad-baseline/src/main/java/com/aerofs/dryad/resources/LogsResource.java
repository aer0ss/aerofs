/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad.resources;

import com.aerofs.dryad.BlacklistedException;
import com.aerofs.dryad.Constants;
import com.aerofs.dryad.config.BlacklistConfiguration;
import com.aerofs.dryad.config.DryadConfiguration;
import com.aerofs.dryad.store.FileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.io.InputStream;
import java.util.UUID;

import static com.aerofs.dryad.Constants.ARCHIVED_LOGS_DIRECTORY;
import static com.aerofs.dryad.Constants.DEFECTS_DIRECTORY;
import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;

@Path("/v1.0")
@Singleton
public final class LogsResource  {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogsResource.class);

    private final FileStore fileStore;
    private final BlacklistConfiguration blacklist;

    public LogsResource(@Context FileStore fileStore, @Context DryadConfiguration configuration) {
        this.fileStore = fileStore;
        this.blacklist = configuration.getBlacklist();
    }

    @PUT
    @Path("/defects/{defect_id}/appliance")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public void storeApplianceDefectsLogs(@Context UriInfo uriInfo, @PathParam("defect_id") String defect, InputStream body) throws Exception {
        LOGGER.info("PUT {}", uriInfo.getPath());

        throwIfInvalidDefect(defect);

        String filePath = format("%s/%s/appliance.zip", DEFECTS_DIRECTORY, defect);
        LOGGER.info("Saving defect {}/appliance to {}", defect, filePath);
        fileStore.storeLogs(body, filePath);
    }

    @PUT
    @Path("/defects/{defect_id}/client/{user_id}/{device_id}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public void storeClientDefectsLogs(@Context UriInfo uriInfo, @PathParam("defect_id") String defect, @PathParam("user_id") String user, @PathParam("device_id") String device, InputStream body) throws Exception {
        LOGGER.info("PUT {}", uriInfo.getPath());

        throwIfInvalid(user, device);
        throwIfInvalidDefect(defect);
        throwIfBlacklisted(user, defect);

        String filePath = format("%s/%s/%s_%s.zip", DEFECTS_DIRECTORY, defect, user, device);
        LOGGER.info("Saving defect {}/client to {}", defect, filePath);
        fileStore.storeLogs(body, filePath);
    }

    @PUT
    @Path("/archived/{user_id}/{device_id}/{filename}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public void storeArchivedLogs(@Context UriInfo uriInfo, @PathParam("user_id") String user, @PathParam("device_id") String device, @PathParam("filename") String filename, InputStream body) throws Exception {
        LOGGER.info("PUT {}", uriInfo.getPath());

        throwIfInvalid(user, device, filename);
        throwIfBlacklisted(user, device);

        String filePath = format("%s/%s/%s_%s", ARCHIVED_LOGS_DIRECTORY, user, device, filename);
        LOGGER.info("Saving archived logs from {}/{} to {}", user, device, filePath);
        fileStore.storeLogs(body, filePath);
    }

    // TODO (AT): there's gotta be a better way of checking this
    private void throwIfInvalid(String... args) throws IllegalArgumentException {
        for (String arg : args) {
            checkArgument(!arg.equals("..") && !arg.equals(".") && !arg.contains("/"), format("Invalid argument: %s", arg));
        }
    }

    private void throwIfInvalidDefect(String defect) throws IllegalArgumentException {
        // new defect ID format.
        if (Constants.ID_FORMAT.matcher(defect).matches()) {
            return;
        }

        // old defect ID format, this throws IllegalArgumentException if invalid
        // noinspection ResultOfMethodCallIgnored
        UUID.fromString(defect);
    }

    private void throwIfBlacklisted(String user, String device) throws BlacklistedException {
        if (blacklist.getUsers().contains(user)) {
            throw new BlacklistedException(user);
        }

        if (blacklist.getDevices().contains(device)) {
            throw new BlacklistedException(device);
        }
    }
}
