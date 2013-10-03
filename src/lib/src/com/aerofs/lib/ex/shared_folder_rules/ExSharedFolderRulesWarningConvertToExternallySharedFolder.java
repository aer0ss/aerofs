/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.ex.shared_folder_rules;

import com.aerofs.base.id.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;
import com.google.common.collect.ImmutableMap;

public class ExSharedFolderRulesWarningConvertToExternallySharedFolder
        extends AbstractExSharedFolderRules
{
    private static final long serialVersionUID = 0;

    public ExSharedFolderRulesWarningConvertToExternallySharedFolder(
            ImmutableMap<UserID, FullName> externalUsers)
    {
        super(externalUsers);
    }

    public ExSharedFolderRulesWarningConvertToExternallySharedFolder(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.SHARED_FOLDER_RULES_WARNING_CONVERT_TO_EXTERNALLY_SHARED_FOLDER;
    }
}
