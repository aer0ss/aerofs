/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.ex.shared_folder_rules;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

// Unlike other ExSharedFolderRules* classes, this class doesn't need to carry the list of external
// users and therefore doesn't inherit from AbstarctExSharedFolderRules
public class ExSharedFolderRulesWarningAddExternalUser
        extends AbstractExWirable
{
    private static final long serialVersionUID = 0;

    public ExSharedFolderRulesWarningAddExternalUser()
    {
    }

    public ExSharedFolderRulesWarningAddExternalUser(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.SHARED_FOLDER_RULES_WARNING_ADD_EXTERNAL_USER;
    }
}
