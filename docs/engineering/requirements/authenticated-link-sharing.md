# Authenticated Link Sharing

We wish to extend the administrator link sharing configuration options. This document describes the extension that will be used to restrict access to links to only authenticated users.

## Existing Product

Link sharing can be:

1. Globally enabled, or
2. Globally disabled

When enabled, all links are public, i.e. they are accessible to anyone who can access AeroFS, even if they do not have an AeroFS account.

Links can have:

* An expiry, and/or
* Passwords



## New Product Requirements

Link sharing can be:

1. Globally enabled for _everyone_ (both users that have AeroFS accounts and
   users that do not have AeroFS accounts), i.e. links are completely public,
   or
2. Globally enabled for _authenticated users_ only, or
3. Globally disabled.

When link sharing is enabled for authenticated users only, the user must log in to be able to access the link.

Links can have:

* An expiry, and/or
* Passwords