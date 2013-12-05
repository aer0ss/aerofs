# Data Leak Prevention - Sharing Rules

    Current revision: 0.1
    Revision history:
      0.1 Initial draft


## Purpose

System administrators should be able to restrict the basic sharing functionality to prevent sensitive information
from leaking across group boundaries.

The desired behavior is to prevent accidental leaks and/or educate users about safe sharing practices.

## Requirements

### Granular permissions

"Monotonic" permissions (i.e `VIEWER < EDITOR < OWNER`) are not flexible enough. The new system will offer
more granular permission control through the use of independent `WRITE` and `MANAGE` flags. For simplicity
the old model will still be used in the user interface.

### Internal/external users

A distinction is made between internal and external users.

For now, a regular expression needs to be supplied and a user is considered internal if his email address
matches that regular expression.

### Restricted external sharing

System administrators can restrict external sharing through the appliance settings by:

  - toggling a boolean flag to that effect
  - providing a suitable pattern to identify internal users

Restricted external sharing enforce the following sharing rules:

  - when an internal user shares a folder with an external user:

      - warn the internal user
      - revoke `WRITE` permission for all internal users

  - when an internal user is given `WRITE` access to an externally shared folder:

      - reject the request
      - list external users having access to the shared folder in the error message

## Future work

The current requirements are designed to address the most immediate concerns of private customers.
They provide basic DLP garantees but are not very flexible and will need to be refined in the next
iteration.

### Groups

A first step towards addressing the lack of flexibility of the above model would be to use a whitelist of
trusted internal users that could be given `WRITE` access to externally shared folders.

The next logical refinement of that idea is to allow system administrators to specify arbitrary group of
users in addition to the default internal/external ones.

For customers using LDAP it may be possible to automatically retrieve group information from the existing
LDAP infrastructure, thereby avoiding duplication and inconsistencies.

### Custom sharing rules

A fairly general restriction of external sharing can make sense for a many private customers, however
it may lack flexibility for some. Hard-coding customer-specific sharing rules would not be scalable, hence
the need to allow system administrators to define custom rules, based on the above-mentioned groups.
