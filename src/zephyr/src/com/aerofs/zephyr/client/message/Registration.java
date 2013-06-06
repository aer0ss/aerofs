package com.aerofs.zephyr.client.message;

import javax.annotation.concurrent.Immutable;

@Immutable
public final class Registration
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
