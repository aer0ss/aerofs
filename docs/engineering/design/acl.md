# ACL System Design Doc

See also docs/design/sharing\_and\_migration.txt on how shared folder management interacts with the ACL system.

# 1 Access Control Lists

## 1.1  Permissions and Roles

Access Control is done by shared folder and user. Each member of a shared folder
has a role within that shared folder. A role is the union of one or more permissions.

The system currently supports the following permissions:

Name     | Description
-------------------------------
`WRITE`  | The user can create, modify and delete files within the shared folder
`MANAGE` | The user can manage members of the shared folder (add, remove, change permissions)

Note that the is no explicit `READ` permission. This is a conscious decision that
flows from the context in which ACLs are used, i.e. the desktop client.

There is no sane way to give a desktop client write access to a shared folder without
also granting read access. The number of technical and UX issues is just too large and
the case of "write-only" access would be much better served by the use of granular OAuth
tokens together with the REST API. Read-only access is therefore the baseline when an
ACL entry does not have any explicit permissions.

In practice the implementation uses an integer to store roles, with each permission
mapping to a specific bit.

## 1.2  Legacy considerations

In the before time, ACLs used the following discrete roles:

Name     | Equivalent permissions
-----------------------------
`VIEWER` |
`EDITOR` | `WRITE`
`OWNER`  | `WRITE`, `MANAGE`

The main advantage of the old model was its simplicity. Unfortunately that came at the expense
of flexibility and in particular clashed with the requirements of some customers. One scenario
where the old model proved inadequate was allowing employees to receive documents from external
collaborators while preventing them from mistakenly leaking internal documents. In that case,
employees should not have `WRITE` permission but still need `MANAGE` permission.

For now the web and desktop frontend still present that old model to users but it would be
a good idea to consider when and how to expose the finer nuances of the new model.


## 1.3  Sharing rules

One goal of the ACL system is to allow customers to enforce complex organization-specific sharing
rules easily. In the short term customers want employees to automatically lose `WRITE` access to
folders that are shared with external collaborators.

In the long run this is likely to prove insufficient. Finer-grained checks based on group membership
tests have already been mentioned by some large customers. The easiest way to offer that without
turning the ACL system into a monster is likely to be through a small Domain-Specific Lanaguage.


# 2 How ACLs are enforced

## 2.1  Enforcement at API entry points

There are three entry points into the system: Ritual API, Rest API, and Mobile API.

- Access to Ritual is limited to localhost, and we assume only local user has access to localhost devices. Therefore, no ACL enforcement is required.
- Access to Rest API and Mobile API requires VIEWER permission to read system state, and EDTIOR to write. (Mobile API is to be removed in the future.)

## 2.2  Enforcement in peer-to-peer communication

Note that the current design doesn't prevent the attack model where the attacker blocks the target device's connection to SP and thus propagation of ACL updates from the server. Also see Section 1.2.1 for a more fundamental deficiency in the design. A deficiency-free design may be very difficult or even impossible given the decentralized nature of the system. In practice, the current design should work well.

Acronyms used in this section identify protocol primitives:

- GVC, GVR: GetVersionsCall and GetVersionsReply
- GCC, GCR: GetComponentCall and GetComponentReply
- NU: NewUpdates

These are the only primitives that are relevant to ACL enforcement. More primitives may be added in the future.

### 2.2.1   Minimal implementation

*Note: Do NOT alter the reference numbers of these rules since they're referred to in the source code.*

- **Rule 1**: When sending data (GVR, GCR): remote user must be VIEWER or above

This is to avoid sending data to non-members.

- **Rule 2**: When receiving data (GCR, GVR, NU): remote user must be EDITOR or above

This is to reject data from VIEWERs and non-members.

Since this rule modifies state transition, it has potential impact on invariants of local systems and eventual consistency of the global system. However, it is easy to show that rejecting all state-transitioning primitives from a device is equivalent to physically isolating this device from the local device. Because the system is designed to handle network partition well, this rule affects neither invariants nor eventual consistency.

Devices receive ACL updates at different times. As a result, when device D gets to know that device E is a VIEWER device, device F may still treat E as an editor and receive data from E. It's important to note that an EDITOR can propagate updates on behalf of other peers. Therefore, even though D will reject data directly from E, it will accept data originated from E and relayed by F.
Corollary: this rule is not fully effective until the last member device receives the ACL update. This gives VIEWER devices a very long attack window to insert edits into the system after they are demoted. This is a deficiency of the current design. The solution is difficult if not impossible. **This deficiency renders the entire ACL enforcement unsuitable as a security measure**.

In practice, however, it is not a severe issue, since:

1.  This attack requires the attacker to first break the authentication system and impersonate a legitimate member, which is non-trivial. Alternatively, the attacker can be a member. In this case, identifying the person is trivial.
2.  Sync History mitigates the damage from such attacks.
3.  Even in the lack of malicious attacks, a legitimate VIEWER's local edits can “leak” to other peers during the large attack window, causing usability problems. Rule 3 below partly addresses this issue.

### 2.2.2   Optional improvements

- **Rule 3**: When sending data (GCR, GVR, NU): local user must be EDITOR or above

This rule is not required, as it doesn't improve security. However, it mitigates the usability problem among legitimate users, as discussed in the previous section: as long as either the sender or the receiver receives the ACL update, data propagation will be blocked.

If the VIEWER and one of the EDITORs happen to be in the same LAN, it's likely that both of them are disconnected from the server and can still able to sync with each other, causing a large attack window. One potential improvement is exchange ACL version numbers peer-to-peer, and if the device finds that its ACL is out-of-date, reject data until the ACL is updated from the server. This way, the EDITOR can receive the latest ACL version number from other EDITORs in the same LAN. However, it doesn't work if no other EDITORs in the same LAN have the latest ACL.

Another solution that guarantees to work is to refresh ACL periodically on all devices, and block data if it fails. This however would make syncing no longer fully decentralized.

- **Rule 4**: When sending requests for data (GCC, GVC): remote user must be EDITOR or above

This rule is not required, as Rule 3 blocks the receiver of the request from serving the data. However, this rule saves the sender round-trip times (and thus improves syncing latency), as well as computer and network resources for the requests.

This rule is not trivial to implement (it requires changing anti-entropy and collector algorithms), and is currently not included in the system.

# 3   Curse of nested sharing

[Note that the problem described below also applies to centralized file syncing systems such as Dropbox.]

When there is a shared subfolder inside another shared folder, the system can choose to inherit or not to inherit ACLs from the parent folder to the subfolder. Both choices have their own issues:

## 3.1 No ACL inheritance

If the system doesn't inherit ACLs, user experience would be unacceptable: Suppose user U has shared a folder F with user V. User U then shares the subfolder G to user W. Without ACL inheritance, V would suddenly lose access to G. Contents under G would stop syncing to V's devices.

## 3.2 With ACL inheritance

If the system does inherit ACLs, there are severe security issues: intentionally or not, users can arbitrarily override ACLs by moving shared folders with less permission into a more permissive folder. This issue can be prevented in traditional filesystems as their ACLs may forbid such movement. In contrast, AeroFS or Dropbox never forbids movement of shared folders.

Another related issue is that a shared folder F may exhibit inconsistent ACLs on different users' devices, if the parent folder in which F resides has different ACLs. Inconsistent ACL would render the system unusable. This issue's fundamental cause is that unlike most traditional filesystems, AeroFS and Dropbox don't have a common root and therefore a consistent hierarchy for all user folders.

## 3.3 Workaround 1: selective inheritance, good for pros

To avoid the above issues, the system can inherit parents' ACLs only when creating but not moving shared folders. However, this would bring another user experience issue: suppose F and G are two parallel folders. They are shared with different sets of users U and V, respectively. When G is moved into F, users in set U \ V can't see G under F since they don't have read access to G, while users U ∩ V do see G under F. Therefore, F is inconsistent when compared among the two user groups. This behavior might be fine for technical users but confusing to laypersons.

## 3.4 Workaround 2: no nested sharing, the Dropbox way

Alternatively, we can disable nested sharing entirely. First, we disallow sharing a folder if one of its parents or children has been shared. Second, a folder is automatically unshared when moved under a shared folder.

A corner case specific to AeroFS is that a parent folder and a subfolder may be shared concurrently from two different devices. This nested relation will not be detected until the next time the two devices sync. Therefore, AeroFS may unshare folders even though no folder movement has occurred. (Note that this scenario is possible only when the two devices belong to the same user. Two different users can't have the same folder hierarchy unless the folder is shared, and nested sharing, which assumes disabled, is necessary to create additional shared folders under the shared hierarchy.)

