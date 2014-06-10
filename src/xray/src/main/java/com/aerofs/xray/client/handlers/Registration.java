package com.aerofs.xray.client.handlers;

import javax.annotation.concurrent.Immutable;

@Immutable
final class Registration
{
    private final int assignedZid;

    public Registration(int assignedZid)
    {
        this.assignedZid = assignedZid;
    }

    public int getAssignedZid()
    {
        return assignedZid;
    }
}
