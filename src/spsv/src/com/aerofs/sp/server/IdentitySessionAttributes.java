package com.aerofs.sp.server;

/**
 * The user's attributes that are found as a byproduct of the authentication flow.
 */
public class IdentitySessionAttributes
{
    public IdentitySessionAttributes(String email, String firstName, String lastName)
    {
        _email = email;
        _firstName = firstName;
        _lastName = lastName;
    }
    public String getEmail() { return _email; }
    public String getLastName() { return _lastName; }
    public String getFirstName() { return _firstName; }

    private String _email;
    private String _firstName;
    private String _lastName;
}