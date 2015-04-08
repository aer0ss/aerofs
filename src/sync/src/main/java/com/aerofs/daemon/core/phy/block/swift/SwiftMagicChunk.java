/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.swift;

import com.aerofs.daemon.core.phy.block.IBlockStorageBackend;
import com.aerofs.daemon.core.phy.block.encrypted.AbstractMagicChunk;
import com.aerofs.lib.SystemUtil.ExitCode;
import org.javaswift.joss.exception.CommandException;
import org.javaswift.joss.exception.CommandExceptionError;

import java.io.IOException;

/**
 * Swift implementation of the MagicChunk
 */
class SwiftMagicChunk extends AbstractMagicChunk
{
    @Override
    public void init_mc(IBlockStorageBackend bsb) throws IOException
    {
        try {
            checkMagicChunk();
        } catch(CommandException e) {
            l.warn(e.getError().toString());
            if (e.getError().equals(CommandExceptionError.ENTITY_DOES_NOT_EXIST)) {
                ExitCode.SWIFT_BAD_CREDENTIALS.exit();
            } else if (e.getError().equals(CommandExceptionError.ACCESS_FORBIDDEN)) {
                ExitCode.SWIFT_BAD_CREDENTIALS.exit();
            } else if (e.getError().equals(CommandExceptionError.NO_END_POINT_FOUND)) {
                ExitCode.SWIFT_BAD_CREDENTIALS.exit();
            } else if (e.getError().equals(CommandExceptionError.UNAUTHORIZED)) {
                ExitCode.SWIFT_BAD_CREDENTIALS.exit();
            } else {
                throw new IOException(e);
            }
        }
    }

    /**
     * Check/upload magic chunk.
     */
    protected void checkMagicChunk() throws IOException, CommandException
    {
        try {
            downloadMagicChunk();
            return;
        } catch (IOException | CommandException e) {
            // Here we can't catch the JavaSwift-specific NotFoundException, because errors are proxied and
            // wrapped into an IOException
            // ... unless we are running unittests !
            CommandException cause = getCauseOfClass(e, CommandException.class);
            if (cause == null) {
                throw e;
            } else if (cause.getHttpStatusCode() == 404 && cause.getError().equals(CommandExceptionError.ENTITY_DOES_NOT_EXIST)) {
                // continue
            } else {
                throw cause;
            }
        }

        // If there is any problem, one of these calls will throw a CommandException
        uploadMagicChunk();
        downloadMagicChunk();
    }
}
