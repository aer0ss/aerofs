/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.ex.shared_folder_rules;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException.Type;

public class ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers extends AbstractExWirable
{
    private static final long serialVersionUID = 0;

    @Override
    public Type getWireType()
    {
        return Type.SHARED_FOLDER_RULES_WARNING_OWNER_CAN_SHARE_WITH_EXTERNAL_USERS;
    }
}
