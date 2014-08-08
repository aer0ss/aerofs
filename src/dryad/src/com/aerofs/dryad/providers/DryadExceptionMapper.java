/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad.providers;

import com.aerofs.dryad.Blacklist.BlacklistedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.Random;

import static java.lang.String.format;

/**
 * Handles all exceptions thrown by Dryad and convert them to http response.
 *
 * For http 500 responses, we generate a local defect ID and report it to the user in the web
 * response.
 */
@Provider
public class DryadExceptionMapper implements ExceptionMapper<Exception>
{
    private final Logger l = LoggerFactory.getLogger(DryadExceptionMapper.class);
    private final Random _r = new Random();

    @Override
    public Response toResponse(Exception e)
    {
        if (e instanceof BlacklistedException) {
            l.warn(e.toString());
            return Response.status(Status.FORBIDDEN).build();
        } else if (e instanceof IllegalArgumentException) {
            l.warn(e.toString());
            return Response.status(Status.BAD_REQUEST).build();
        } else {
            // let's stick to positive longs in case someone reports this ID over the phone
            long defectID = Math.abs(_r.nextLong());

            if (e instanceof IOException) {
                // suppress stack trace
                l.error("Defect {}: {}", defectID, e.toString());
            } else {
                // prints stack trace
                l.error("Defect {}: {}", defectID, e.toString(), e);
            }

            return Response
                    .status(Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.TEXT_PLAIN_TYPE)
                    .entity(formatErrorMessage(defectID, e))
                    .build();

        }
    }

    private String formatErrorMessage(long defectID, Exception e)
    {
        String message = e.getCause() == null
                ? format("The error was \"%s\"", e.toString())
                : format("The error was caused by \"%s\"", e.getCause());

        return format("The server has encountered an error while processing your request.\n\n" +
                "%s\n\n" +
                "The error has been logged with ID %s.",
                message, defectID);
    }
}
