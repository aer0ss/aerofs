/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.ex.sharing_rules;

import com.aerofs.base.id.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.proto.Common.PBException;
import com.google.common.collect.ImmutableMap;

public class ExSharingRulesError extends AbstractExSharingRules
{
    private static final long serialVersionUID = 0L;

    public ExSharingRulesError(DetailedDescription.Type type, ImmutableMap<UserID, FullName> users)
    {
        super(new DetailedDescription(type, users));
    }

    public ExSharingRulesError(PBException pb)
    {
        super(pb);
    }

    public DetailedDescription description()
    {
        return decodedExceptionData(DetailedDescription.class);
    }

    @Override
    public PBException.Type getWireType()
    {
        return PBException.Type.SHARING_RULES_ERROR;
    }
}
