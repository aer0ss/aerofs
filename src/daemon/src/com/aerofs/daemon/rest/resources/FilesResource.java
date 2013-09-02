/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.resources;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.CoreIMCExecutor;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.InputChecker;
import com.aerofs.daemon.rest.RestObject;
import com.aerofs.daemon.rest.event.EIFileContent;
import com.aerofs.daemon.rest.event.EIFileInfo;
import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeRangeSet;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@javax.ws.rs.Path("/0/users/{user}/files/{object}")
@Produces(MediaType.APPLICATION_JSON)
public class FilesResource
{
    private static final Logger l = Loggers.getLogger(FilesResource.class);

    private final IIMCExecutor _imce;
    private final InputChecker _inputChecker;

    @Inject
    public FilesResource(CoreIMCExecutor imce, InputChecker inputChecker)
    {
        _imce = imce.imce();
        _inputChecker = inputChecker;
    }

    @GET
    public Response metadata(@PathParam("user") String user, @PathParam("object") String object)
    {
        UserID userid = _inputChecker.user(user);
        RestObject obj = _inputChecker.object(object, userid);
        return new EIFileInfo(_imce, userid, obj).execute();
    }

    @GET
    @javax.ws.rs.Path("/content")
    public Response content(@PathParam("user") String user, @PathParam("object") String object,
            @HeaderParam("If-Range") String ifRange, @HeaderParam("Range") String range)
    {
        UserID userid = _inputChecker.user(user);
        RestObject obj = _inputChecker.object(object, userid);
        return new EIFileContent(_imce, userid, obj, parseEtag(ifRange), range).execute();
    }


    private static @Nullable EntityTag parseEtag(String str)
    {
        try {
            return EntityTag.valueOf(str);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

