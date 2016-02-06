User sign-up workflow
=====================

There are three ways a new user can sign up to the system:

- Case 1. Request a sign-up verification email, via `SP.RequestToSignUp()`
- Case 2. Be Invited to a shared folder by a folder Owner, via `SP.ShareFolder()`
- Case 3. Be invited to an organization by an admin. via `SP.InviteToOrganization()`

Depending on whether the system is a public deployment or private deployment, the 
processing for each case varies.

In private deployment, there is only a single _private organization_ which affects 
handling for Case 3.

Also in private deployment, two types of users may co-exist in one system instance:
_auto-provisioned_ users and _manually-provisioned_ users. Auto-provisioned users are 
the ones managed by external ID management such as LDAP or OpenID. The other type is
managed by AeroFS. See [identity management](../requirements/id_management.html) on 
how the system distinguishes the two types. The sign-up handling for these two types
also differ.

In the following text, trivial details are left out. Please refer to the code for
the exact logic.

# Public deployment

## Case 1

The system sends a **signup verification email** with a signup code. The signup process
creates a new organization for the user.

## Case 2

### If the invitee already exists

Send a **folder invitation email** with the accept link.

###If the invitee doesn't exist

Send a **signup invitation email** with a signup code. The signup process creates a new organization for the user.

When the user installs a desktop client, the UI prompts him to accept the folder. TODO (WW) this will be changed. Instead, the system will prompt the user to accept the invitation
during the signup process on the Web.

## Case 3

Note that a user may be invited to multiple organizations as the same time.

The system use an organization invitation table (sp_organization_invite) to keep track of organization invitations, as well as to map signup code to the invitations.

When the user accepts an organization invitation, he will be moved to the new 
organization, and the corresponding invitation will be removed.

### If the invitee already exists

Send a **organization invitation email** with the accept link.

### If the invitee doesn't exist

Send a **signup invitation email** with a signup code. The system also maps the code 
to the invitation. As soon as the user signs up using the signup code, the system 
automatically accept the invitation.


# Private deployment: auto-provisioned users

The system automatically creates an AeroFS account for an auto-provisioned user
once he is authenticated by the external system.

## Case 1

The system doesn't allow auto-provisioned users to request for signup.

## Case 2

### If the invitee already exists

The same as in public deployment.

###If the invitee doesn't exist

Send a **signup invitation email** with the login (not signup) link and the 
following instruction:

    When prompted, please use your LDAP (or OpenID or a customized string) 
    account (<email>) to log in.

Upon provisioning, the use joins the single private organization. The rest processing
is the same as in public deployment.

## Case 3

### If the invitee already exists

Then admin can't invite the user since he is already in the private organization.

###If the invitee doesn't exist

Send a **signup invitation email** with the login (not signup) link and the 
instruction identical to the one in Case 2. Once the user is auto-provisioned, the 
system joins the user to the private organization and removes the invitation.

# Private deployment: manually-provisioned users

"Manual" implies that admins or folder owners need to manually invite a user, or the
user needs to manually request for signup, before he can join the system.

## Case 1

By default, manually-provisioned users are not allowed to request for signup unless 
the configuration property `signup_restriction` is 'UNRESTRICTED'
(see [here](../conf_properties.html) for detail).

If it is allowed, the system sends a **signup verification email** with a signup code.
The signup process joins the user to the private organization.

## Case 2

### If the invitee already exists

The same as in public deployment.

###If the invitee doesn't exist

Send a **signup invitation email** with a signup code. The signup process joins the
user to the private organization. The rest processing is the same as in public
deployment.

## Case 3

### If the invitee already exists

The same as auto-provisioned users.

###If the invitee doesn't exist

Send a **signup invitation email** with a signup code. The rest is the same as
auto-provisioned users.

When the admin removes the organization invitation, the system should remove all
signup codes for the user to prevent unauthorized sign-ups.
