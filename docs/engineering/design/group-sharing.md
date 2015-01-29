# AeroFS Group Sharing

Read first: [Group Sharing Requirements](../requirements/group_sharing.html)

---

## Design

### Group Common Name, Existence Tracking

Groups are tracked in the `sp_sharing_groups` table as follows:


| Field            | Type               |
|:-----------------|:-------------------|
| sg_gid           | integer            |
| sg_common_name   | string             |
| sg_org_id        | integer            |
| sg_external_id   | string             |

Unique by `sg_gid` and indexed by `sg_common_name` for auto-complete (FUTURE requirement).

In this table, when `sg_external_id` is null, then the group is locally managed, else it is
externally managed by some AD/LDAP infrastructure. In this case, `sg_external_id` points to the
unique identifier used for synchronization (see 'External Synchronization', below).

Each group will have a non-unique common-name. It is up to the client to manage common names such
that there are no collisions (which would result in end-user confusion, but not database
inconsistency).

### Group Membership

User membership in a particular group is tracked by the `sp_sharing_group_members` table as
follows:

| Field            | Type               |
|:-----------------|:-------------------|
| gm_gid           | integer            |
| gm_member_id     | string             |

N.B. if we decide to expose group management to end-users we can add a notion of a group owner here.
This is omitted for now to avoid the hassle of managing owner migration on user deletion.

### Share Membership

Group membership in a particular share is tracked b the `sp_sharing_group_shares` table as follows:

| Field            | Type               |
|:-----------------|:-------------------|
| gs_gid           | integer            |
| gs_sid           | binary             |
| gs_role          | enum               |

Where `{gs_gid, gs_sid}` is a unique key.

### ACLs and Group Sharing

While there are many possible designs here, two implementations were considered in detail. They are
as follows. The second implementation has been selected.

#### Single ACL & Client-Side Group Resolution

In this scheme, we create a single ACL `sp_acl` table when a group is added to a share. The client
is responsible for resolving the group ID into a list of users.

Advantages:

* Scalable server-side group deletion.

Disadvantages:

* Client-side complexity increased; clients must now be aware of the notion of groups.
* Security implications if caching group resolution client-side.

#### Multiple ACLs

In this scheme, multiple ACLs are added to the `sp_acl` table and are marked as belonging to a
particular group using a new `a_gid` column. As part of this change, the ACL table primary key
changes to `{user, sid, gid}`.

On read, we must compute the aggregate ACL permission, which is the max of all permissions (see
requirements docs for more on permission precedence).

The supporting protocol and database code should be implemented such that the ACL table cannot
have multiple ACLs belonging to one user and share in different states. This inconsistency would
be very confusing to the user. Implementation note: it is very important this is well documented
in the code.

Advantages:

* Client-side implementation does not need to change considerably (adjustments will be necessary to
allow for duplicate users and permission precedence, but this is true in either implementation and
can potentially be done server-side).

Disadvantages:

* Server-side group deletion not as scalable.
* Web facing calls must be updated to aggregate users into groups.

#### Decision

The 'Multiple ACLs' implementation has been selected because:

1. Scalability bottlenecks with respect to group deletion are expected to be minimal. The same
scalability issues already exist for shared folder deletion.
2. Increased complexity in web facing calls is preferred over increased complexity in client-side
ACL handling.

#### Operations

It is worth going over the set of operations this schema will support. These descriptions should be
used as a guideline for test coverage and as an implementation helper.

In all examples below we refer to hypothetical user `U` and group `G`.

1. User added to group.
    * Insert into `sp_sharing_group_members` group `G` user `U`.
    * Foreach share `S` in `sp_sharing_group_shares` belonging to `G`:
        * Invite user `U` to `S`. (Send an email to `U`. Create an ACL for `U` in `sp_acl` for `S`
        with `a_gid` set to `G`. N.B. ACLs are now unique by `{user, sid, gid}`. If ACLs already
        exist for the given `{user, sid}` pair, use their state, otherwise set the state of the new
        ACL to PENDING.)

2. User removed from group.
    * Foreach ACL in `sp_acl` for user `U` belonging to group `G`:
        * Delete ACL.
    * Delete user `U` from `sp_sharing_group_members`.

3. Group added to share.
    * Foreach user `U` in `sp_sharing_group_members` belonging to `G`:
        * Invite `U` to `S`.
    * Insert into `sp_sharing_group_shares` share `S` group `G`.

4. Group removed from share.
    * Foreach user `U` in `sp_sharing_group_members` belonging to `G`:
        * Delete ACL entry for `U` belonging to group `G` in `sp_acl`.

5. Group deleted.
    * Foreach user `U` in `sp_sharing_group_members` belonging to `G`:
        * Remove user from group (see #2).
    * Delete group in `sp_sharing_groups`.

### External Synchronization

Synchronization will occur daily at midnight or on-demand through an API call.

The `g_external_id` field is used to uniquely identify the group in the 3rd party system.

#### LDAP Specifics

Unfortunately, LDAP does not provide a strong unique identifier for groups. The DN for a group is
typically a combination of the common name and the location, e.g.
`cn=AeroFS Developers,dc=example,dc=com`, where the CN is an editable field (*quiet whimpering*).
The DN can be arbitrarily long. Therefore, for our purposes, we will hash the DN and use that as our
unique external ID.

It is worth noting that if an IT admin changes the common name of a group this will result in the
synchronizer detecting a deletion and a creation. Alas, there is no way around this.
