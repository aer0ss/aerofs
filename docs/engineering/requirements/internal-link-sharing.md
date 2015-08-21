# Internal Link Sharing Requirements

We wish to extend the admin's link sharing configuration options. This
documents describes the extension that will be used to faciliate "Internal Link
Sharing".

## Existing Product

Link sharing can be:

1. Globally enabled, or
2. Globally disabled

When enabled, all links are public, i.e. they are accessible to anyone, even if
they do not have an AeroFS account.

Links can have:

* Expiry, and/or
* Passwords

## New Product Requirements

Link sharing can be:

1. Globally enabled for _everyone_ (both users that have AeroFS accounts and
   users that do not have AeroFS accounts), i.e. links are completely public,
   or
2. Globally enabled for _internal users_ only, or
3. Globally disabled.

When link sharing is enabled for internal users only, you must log in to be
able to access the link.

Links can have:

* Expiry, and/or
* Passwords

(This piece remains unchanged).
