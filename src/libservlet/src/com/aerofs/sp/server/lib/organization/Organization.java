package com.aerofs.sp.server.lib.organization;

public class Organization
{
    public final OrgID _id;
    public final String _name;

    public Organization(OrgID id, String name)
    {
        _id = id;
        _name = name;
    }

    @Override
    public int hashCode()
    {
        return _id.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null && _id.equals(((Organization)o)._id));
    }

    @Override
    public String toString()
    {
        return "org #" + _id + " (" + _name + ")";
    }
}
