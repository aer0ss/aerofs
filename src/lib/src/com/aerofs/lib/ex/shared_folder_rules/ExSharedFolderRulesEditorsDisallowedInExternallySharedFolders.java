/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.ex.shared_folder_rules;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.proto.Common.PBException.Type;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;

import java.nio.charset.Charset;

public class ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders extends AbstractExWirable
{
    private static final long serialVersionUID = 0;

    public ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders(
            ImmutableMap<UserID, FullName> externalUsers)
    {
        super(getData(externalUsers));
    }

    static byte[] getData(ImmutableMap<UserID, FullName> externalUsers)
    {
        return new Gson().toJson(externalUsers).getBytes(Charset.forName("UTF-8"));
    }

    @Override
    public Type getWireType()
    {
        return Type.SHARED_FOLDER_RULES_EDITORS_DISALLOWED_IN_EXTERNALL_SHARED_FOLDER;
    }
}
