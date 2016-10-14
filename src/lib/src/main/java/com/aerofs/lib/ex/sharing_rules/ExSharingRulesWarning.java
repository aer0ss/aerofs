/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.ex.sharing_rules;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;
import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.List;

public class ExSharingRulesWarning extends AbstractExSharingRules
{
    private static final long serialVersionUID = 0L;

    public ExSharingRulesWarning(List<DetailedDescription> d)
    {
        super(d);
    }

    public ExSharingRulesWarning(PBException pb)
    {
        super(pb);
    }

    @SuppressWarnings("unchecked")
    public ImmutableList<DetailedDescription> descriptions()
    {
        return ImmutableList.copyOf(_decodedExceptionData);
    }

    @Override
    public Type getWireType()
    {
        return Type.SHARING_RULES_WARNINGS;
    }

    @Override
    public String toString()
    {
        return descriptions().toString();
    }
}
