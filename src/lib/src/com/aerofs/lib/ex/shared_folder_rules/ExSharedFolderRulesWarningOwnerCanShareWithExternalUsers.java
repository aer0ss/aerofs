/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.ex.shared_folder_rules;

import com.aerofs.base.id.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;
import com.google.common.collect.ImmutableMap;

public class ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers
        extends AbstractExSharedFolderRules
{
    private static final long serialVersionUID = 0;

    public ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers(
            ImmutableMap<UserID, FullName> externalUsers)
    {
        super(externalUsers);
    }

    public ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.SHARED_FOLDER_RULES_WARNING_OWNER_CAN_SHARE_WITH_EXTERNAL_USERS;
    }
}
