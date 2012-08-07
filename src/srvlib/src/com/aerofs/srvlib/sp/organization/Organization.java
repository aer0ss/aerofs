package com.aerofs.srvlib.sp.organization;

public class Organization
{
    public final String _id;
    public final String _name;
    public final String _allowedDomain;
    public final boolean _shareExternally;

    // An organization with _allowedDomain matching ANY_DOMAIN permits users with any email
    // address to sign up.
    public static final String ANY_DOMAIN = "*";

    public Organization(String id, String name, String domain, boolean shareExternally)
    {
        _id = id;
        _name = name;
        _allowedDomain = domain;
        _shareExternally = shareExternally;
    }

    /**
     * @return true iff the domain of the given emailAddress matches the allowed domain of this org
     * N.B. the comparison is case-insensitive.
     */
    public boolean domainMatches(String emailAddress)
    {
        if (_allowedDomain.equals(ANY_DOMAIN)) {
            return true;
        } else {
            String emailDomain = emailAddress.substring(emailAddress.indexOf('@') + 1);
            assert !emailDomain.isEmpty();
            return emailDomain.equalsIgnoreCase(_allowedDomain);
        }
    }

    @Override
    public boolean equals(Object o)
    {
        return _id.equals(((Organization)o)._id);
    }

    @Override
    public String toString()
    {
        return _name;
    }
}
