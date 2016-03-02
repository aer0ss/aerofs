/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExExternalServiceUnavailable;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.authentication.LdapAuthority;
import com.aerofs.sp.authentication.LdapConfiguration;
import com.aerofs.sp.authentication.LdapConnector;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.group.Group.AffectedUserIDsAndInvitedFolders;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.ObjectArrays;
import com.google.common.collect.Sets;
import com.mysql.jdbc.StringUtils;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPURL;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

//TODO (RD) since this class is serial, is there any point to opening and closing LDAPConnections?
// either just have one, or make it parallel
public class LdapGroupSynchronizer
{

    public LdapGroupSynchronizer(LdapConfiguration cfg, User.Factory userFact, Group.Factory groupFact,
            InvitationHelper invitationHelper)
    {
        _cfg = cfg;
        _ldapAuthority = new LdapAuthority(_cfg);
        _connector = new LdapConnector(_cfg);
        _groupFact = groupFact;
        _userFact = userFact;
        _invitationHelper = invitationHelper;

        try {
            _md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            _l.error("missing algorithm to hash group external IDs, exiting");
            throw new RuntimeException(e);
        }

        loadConfig();
    }

    private void loadConfig() {
        // default query attributes to differentiate between and construct Users and Groups
        // need email to create UserID and group_name to create Group if it doesn't exist yet
        _queryAttrs = new String[]{_cfg.USER_EMAIL, _cfg.GROUP_NAME, "objectClass"};

        List<String> allAttrs = Lists.newArrayListWithCapacity(
                _cfg.GROUP_STATIC_MEMBERS.size() +
                        _cfg.GROUP_DYNAMIC_MEMBERS.size() +
                        (StringUtils.isNullOrEmpty(_cfg.GROUP_UID_MEMBER) ? 0 : 1));
        allAttrs.addAll(_cfg.GROUP_DYNAMIC_MEMBERS);
        allAttrs.addAll(_cfg.GROUP_STATIC_MEMBERS);
        allAttrs.add(_cfg.GROUP_UID_MEMBER);
        _allMemberAttrs = allAttrs.toArray(new String[allAttrs.size()]);
    }

    @Override
    public String toString()
    {
        return "LDAP Group Synchronizer";
    }

    // this method is synchronized to avoid concurrent attempts to sync groups
    public synchronized AffectedUsersAndError synchronizeGroups(SQLThreadLocalTransaction sqlTrans,
            Organization org)
            throws ExExternalServiceUnavailable, LDAPException
    {
        coveredGroups = Maps.newHashMap();
        affected = ImmutableSet.builder();
        boolean errored = false;
        _sqlTrans = sqlTrans;
        _organization = org;

        for (SearchResultEntry entry : groupsToSync().getSearchEntries()) {
            // try-catch is inside the for loop here so we can optimistically do as much work
            // as possible here
            try {
                syncGroupEntry(entry);
            } catch (Exception e) {
                _l.warn("encountered an exception while syncing groups with LDAP endpoint", e);
                errored = true;
            }
        }

        Set<String> coveredHashes = coveredGroups.keySet();
        // if we error on trying to get externalIDs, default to an empty set
        // this way deletedGroups will be empty and no groups will be deleted
        Set<String> deletedGroups = Sets.newHashSet(externalIDsOrEmpty(_organization));
        deletedGroups.removeAll(coveredHashes);
        for (String externalID : deletedGroups) {
            // try-catch is inside the for loop here so we can optimistically do as much work
            // as possible here
            try {
                deleteGroupWithExternalID(externalID);
            } catch (Exception e) {
                _l.warn("encountered an exception while deleting externally-managed groups", e);
                errored = true;
            }
        }
        // don't have to create new groups here, already handled in groupFromDN
        return new AffectedUsersAndError(errored, affected.build());
    }

    private List<String> externalIDs(Organization org)
            throws SQLException
    {
        _sqlTrans.begin();
        try {
            List<String> groups = org.getExternalIDs();
            _sqlTrans.commit();
            return groups;
        } catch (Exception e) {
            _sqlTrans.rollback();
            throw e;
        }
    }

    private List<String> externalIDsOrEmpty(Organization org)
    {
        try {
            return externalIDs(org);
        } catch (SQLException e) {
            return Lists.newLinkedList();
        }
    }

    // returns an entry with all member attributes available from the object at the specified dn
    private SearchResultEntry getMembersOfGroupWithDN(String dn)
            throws ExExternalServiceUnavailable, LDAPException
    {
        LDAPConnectionPool pool = _connector.getPool();
        LDAPConnection conn = _connector.getConnectionFromPool(pool);
        try {
            SearchResult members = conn.search(dn, SearchScope.BASE, anyGroup(), _allMemberAttrs);
            if (members.getEntryCount() != 1) {
                throw new LDAPException(ResultCode.INVALID_DN_SYNTAX, "provided Group DN of " + dn
                    + " matches " + members.getEntryCount() + " entities");
            }
            return members.getSearchEntry(dn);
        } finally {
            pool.releaseAndReAuthenticateConnection(conn);
        }
    }

    private ImmutableSet<UserID> usersInGroup(String groupDN)
            throws ExExternalServiceUnavailable, LDAPException, SQLException, ExNotFound,
            ExNoAdminOrOwner, IOException, ExAlreadyExist
    {
        String externalID = new String(hashOfDN(groupDN));
        // avoid doing duplicate work, if this group has already been traversed then exit early
        if (coveredGroups.containsKey(externalID)) {
            if (coveredGroups.get(externalID) == null) {
                // encountered cyclical group dependencies
                _l.error("encountered cyclical group dependencies in LDAP, group memberships may " +
                        "be inaccurate");
                coveredGroups.put(externalID, ImmutableSet.of());
            }
            return coveredGroups.get(externalID);
        }
        // put a null value so we know we've started processing this group and we can catch cyclical
        // group memberships
        coveredGroups.put(externalID, null);

        SearchResultEntry entry = getMembersOfGroupWithDN(groupDN);

        String[] staticMemberStrings = aggregateAttributeValues(entry, _cfg.GROUP_STATIC_MEMBERS);
        String[] dynamicMemberStrings = aggregateAttributeValues(entry, _cfg.GROUP_DYNAMIC_MEMBERS);

        String[] uniqueMemberStrings = entry.getAttributeValues(_cfg.GROUP_UID_MEMBER) != null ?
            entry.getAttributeValues(_cfg.GROUP_UID_MEMBER) : new String[0];

        ImmutableSet<UserID> emailsOfMembers = emailsOfMembers(staticMemberStrings,
                dynamicMemberStrings, uniqueMemberStrings);
        coveredGroups.put(externalID, emailsOfMembers);
        return emailsOfMembers;
    }

    // returns an array of strings of all the values for the specified attributes on the entry
    private String[] aggregateAttributeValues(Entry entry, List<String> attributes)
    {
        String[] values = new String[0];
        for (String attribute : attributes) {
            if (entry.getAttributeValues(attribute) != null) {
                values = ObjectArrays.concat(values,
                        entry.getAttributeValues(attribute), String.class);
            }
        }
        return values;
    }

    // syncs the members of the group matching groupEntry to the LDAP representation
    // will make a new group if it doesn't exist within AeroFS yet
    private void syncGroupEntry(Entry groupEntry)
            throws Exception
    {
        String groupDN = groupEntry.getDN();

        ImmutableSet<UserID> ldapUserEmails = usersInGroup(groupDN);
        Group matching = groupFromEntry(groupEntry);
        ImmutableSet<User> ldapUsers = userSetFromEmails(ldapUserEmails);
        ImmutableSet<User> existingUsers = usersOfGroup(matching);

        Set<User> usersToAdd = Sets.difference(ldapUsers, existingUsers);
        Set<User> usersToRemove = Sets.difference(existingUsers, ldapUsers);

        _sqlTrans.begin();
        try {
            for (User newMember : usersToAdd) {
                AffectedUserIDsAndInvitedFolders result = matching.addMember(newMember);
                affected.addAll(result._affected);

                // N.B. do not .send() the invitation emailer; we do not want to spam users with group invitation
                // emails when doing an LDAP sync.
                _invitationHelper.createBatchFolderInvitationAndEmailer(matching,
                        // use unknown team server so in the emails sender appears as "an admin"
                        _userFact.create(UserID.UNKNOWN_TEAM_SERVER), newMember, result._folders);
            }

            for (User oldMember : usersToRemove) {
                affected.addAll(matching.removeMember(oldMember, null));
            }

            String ldapCommonName = groupEntry.getAttributeValue(_cfg.GROUP_NAME);
            if (ldapCommonName != null && !matching.getCommonName().equals(ldapCommonName)) {
                matching.setCommonName(ldapCommonName);
            }
            _sqlTrans.commit();
        } catch (Exception e) {
            _sqlTrans.rollback();
            throw e;
        }
    }

    private void deleteGroupWithExternalID(String externalID)
            throws SQLException, ExNoAdminOrOwner, ExNotFound, ExNoPerm
    {
        _sqlTrans.begin();
        try {
            affected.addAll(_groupFact.createFromExternalID(externalID.getBytes()).delete());
            _sqlTrans.commit();
        } catch (Exception e) {
            _sqlTrans.rollback();
            throw e;
        }
    }

    private ImmutableSet<User> userSetFromEmails(ImmutableCollection<UserID> emails)
    {
        ImmutableSet.Builder<User> users = ImmutableSet.builder();
        for (UserID email : emails) {
            users.add(_userFact.create(email));
        }
        return users.build();
    }

    private ImmutableSet<User> usersOfGroup(Group group)
            throws SQLException, ExNotFound
    {
        ImmutableSet.Builder<User> users = ImmutableSet.builder();
        _sqlTrans.begin();
        try {
            users.addAll(group.listMembers());
            _sqlTrans.commit();
        } catch (Exception e) {
            _sqlTrans.rollback();
            throw e;
        }
        return users.build();
    }

    // given the values for different types of member attributes, returns emails of group members
    private ImmutableSet<UserID> emailsOfMembers(String[] staticAttrValues,
            String[] dynamicAttrValues, String[] uniqueAttrValues)
            throws ExExternalServiceUnavailable, ExNoAdminOrOwner, ExAlreadyExist, ExNotFound,
            IOException, SQLException, LDAPException
    {
        ImmutableSet.Builder<UserID> emails = ImmutableSet.builder();
        List<Entry> nestedGroupEntries = Lists.newLinkedList();
        addStaticMembers(staticAttrValues, emails, nestedGroupEntries);
        addDynamicMembers(dynamicAttrValues, emails, nestedGroupEntries);
        addUniqueMembers(uniqueAttrValues, emails, nestedGroupEntries);

        // N.B. leave processing of nested groups until all LDAP connections are closed
        // otherwise would deplete pool through recursion
        for (Entry nestedGroup : nestedGroupEntries) {
            emails.addAll(usersInGroup(nestedGroup.getDN()));
        }
        return emails.build();
    }

    // adds the emails of users specified by a memberDN to emails, and the groups specified by a
    // memberDN to nestedGroups
    private void addStaticMembers(String[] memberDNs, ImmutableCollection.Builder<UserID> emails,
            List<Entry> nestedGroups)
            throws ExExternalServiceUnavailable, LDAPException
    {
        LDAPConnectionPool pool = _connector.getPool();
        LDAPConnection conn = _connector.getConnectionFromPool(pool);
        try {
            for (String memberDN : memberDNs) {
                SearchResult member = conn.search(memberDN, SearchScope.BASE,
                        Filter.createORFilter(anyUser(), anyGroup()), _queryAttrs);
                int entries = member.getEntryCount();
                // entries == 0 means that the member is some user or group outside of scope,
                // or a different type of object
                if (entries == 1) {
                    processEntry(member.getSearchEntry(memberDN), emails, nestedGroups);
                } else if (entries != 0) {
                    throw new LDAPException(ResultCode.INVALID_DN_SYNTAX, "member dn of " + memberDN +
                            "in group does not uniquely specify an LDAP entry");
                }
            }
        } finally {
            pool.releaseAndReAuthenticateConnection(conn);
        }
    }

    // adds the emails of users matching a searchURL to emails, and the groups matching a
    // searchURL to nestedGroups
    private void addDynamicMembers(String[] searchURLs, ImmutableCollection.Builder<UserID> emails,
            List<Entry> nestedGroups)
            throws ExExternalServiceUnavailable, LDAPException
    {
        LDAPConnectionPool pool = _connector.getPool();
        LDAPConnection conn = _connector.getConnectionFromPool(pool);
        try {
            for (String ldapURL : searchURLs) {
                SearchRequest ldapQuery = new LDAPURL(ldapURL).toSearchRequest();
                Filter restrictiveFilter = Filter.createANDFilter(ldapQuery.getFilter(),
                        Filter.createORFilter(anyUser(), anyGroup()));
                ldapQuery.setFilter(restrictiveFilter);
                ldapQuery.setAttributes(_queryAttrs);
                for (SearchResultEntry entry : conn.search(ldapQuery).getSearchEntries()) {
                    processEntry(entry, emails, nestedGroups);
                }
            }
        } finally {
            pool.releaseAndReAuthenticateConnection(conn);
        }
    }

    // adds the emails of users with a specified memberUid to emails, and the groups with a
    // specified memberUid to nestedGroups
    private void addUniqueMembers(String[] memberUids, ImmutableCollection.Builder<UserID> emails,
            List<Entry> nestedGroups)
            throws ExExternalServiceUnavailable, LDAPException
    {
        LDAPConnectionPool pool = _connector.getPool();
        LDAPConnection conn = _connector.getConnectionFromPool(pool);
        try {
            for (String uniqueValue : memberUids) {
                Entry member = getUserWithUid(conn, uniqueValue);
                if (member != null) {
                    addUserEmailFromEntry(member, emails);
                } else {
                    // it wasn't a user, might be a nested group
                    // N.B. the uid attribute is primarily for posixGroup, which doesn't allow
                    // nested groups. If there's no LDAP group that uses UIDs and allows nesting,
                    // we could remove this block
                    member = getGroupWithUid(conn, uniqueValue);
                    if (member != null) {
                        addGroupToNestedGroups(member, nestedGroups);
                    }
                    // we don't deal with non-user and non-group members, or it could have failed
                    // a filter somewhere
                }
            }
        } finally {
            pool.releaseAndReAuthenticateConnection(conn);
        }
    }

    private @Nullable Entry getUserWithUid(LDAPConnection conn, String uid)
            throws LDAPException
    {
        SearchResult member = conn.search(_cfg.USER_BASE, _cfg.USER_SCOPE,
                Filter.createANDFilter(anyUser(), Filter.createEqualityFilter(_cfg.COMMON_UID_ATTRIBUTE, uid)),
                _cfg.USER_EMAIL);
        int entries = member.getEntryCount();
        if (entries == 1) {
            return member.getSearchEntries().get(0);
        } else if (entries == 0) {
            return null;
        } else {
            throw new LDAPException(ResultCode.INVALID_DN_SYNTAX, "unique member attribute "
                    + "value of " + uid + " was not unique on users");
        }
    }

    private @Nullable Entry getGroupWithUid(LDAPConnection conn, String uid)
            throws LDAPException
    {
        SearchResult member = conn.search(_cfg.GROUP_BASE, _cfg.GROUP_SCOPE,
                Filter.createANDFilter(anyGroup(), Filter.createEqualityFilter(_cfg.COMMON_UID_ATTRIBUTE, uid)),
                _cfg.GROUP_NAME);
        int entries = member.getEntryCount();
        if (entries == 1) {
            return member.getSearchEntries().get(0);
        } else if (entries == 0) {
            return null;
        } else {
            throw new LDAPException(ResultCode.INVALID_DN_SYNTAX, "unique member attribute "
                    + "value of " + uid + " was not unique on groups");
        }
    }

    // Given an LDAP entry, if the entry is a User then adds the user's email into emails;
    // if the entry is a group, add it to nestedGroups
    private void processEntry(Entry entry, ImmutableCollection.Builder<UserID> emails,
            List<Entry> nestedGroups)
    {
         if (entry.getObjectClassAttribute().hasValue(_cfg.USER_OBJECTCLASS)) {
             addUserEmailFromEntry(entry, emails);
        } else {
            for (String groupObjectClass : _cfg.GROUP_OBJECTCLASSES) {
                if (entry.getObjectClassAttribute().hasValue(groupObjectClass)) {
                    addGroupToNestedGroups(entry, nestedGroups);
                    return;
                }
            }
        }
    }

    private void addUserEmailFromEntry(Entry entry, ImmutableCollection.Builder<UserID> emails)
    {
        try {
            UserID ldapEmail = UserID.fromExternal(entry.getAttributeValue(_cfg.USER_EMAIL));
            // only allow users that match all the requirements of the LDAP Identity system
            // canAuthenticate ignores the additional filter, but we don't - see anyUser()
            if (_ldapAuthority.canAuthenticate(ldapEmail)) {
                emails.add(ldapEmail);
            }
        } catch (ExInvalidID e) {
            _l.warn("invalid email address of: {} for user in ldap group",
                    entry.getAttributeValue(_cfg.USER_EMAIL));
        } catch (ExExternalServiceUnavailable e) {
            // this doesn't even make any sense, we're in the middle of a transaction with the
            // same external service
            _l.error("lost connectivity with the ldap server while syncing groups");
        }
    }

    private void addGroupToNestedGroups(Entry entry, List<Entry> nestedGroups)
    {
        nestedGroups.add(entry);
    }

    private Group groupFromEntry(Entry entry)
            throws SQLException
    {
        byte[] digest = hashOfDN(entry.getDN());
        _sqlTrans.begin();
        try {
            Group group = _groupFact.createFromExternalID(digest);
            if (group == null) {
                // group doesn't exist yet, will have to create it
                OrganizationID orgID = _organization.id();
                // default to the group's DN if it doesn't have a cn attribute
                String commonName = entry.getAttributeValue(_cfg.GROUP_NAME) == null ?
                        entry.getDN() : entry.getAttributeValue(_cfg.GROUP_NAME);
                group = _groupFact.save(commonName, orgID, digest);
            }
            _sqlTrans.commit();
            return group;
        } catch (Exception e) {
            _sqlTrans.rollback();
            throw e;
        }
    }

    private byte[] hashOfDN(String dn)
    {
        try {
            _md.update(dn.getBytes("UTF-8")); // TODO (RD) is this the right encoding
        } catch (UnsupportedEncodingException e) {
            _l.error("system doesn't support unicode encoding of group names");
            throw new RuntimeException(e);
        }
        return _md.digest();
    }

    // N.B. returns groups that actually need to be synced with AeroFS, though other LDAP groups
    // may also be traversed in the case of nested groups
    private SearchResult groupsToSync()
            throws ExExternalServiceUnavailable, LDAPException
    {
        LDAPConnectionPool pool = _connector.getPool();
        LDAPConnection conn = _connector.getConnectionFromPool(pool);
        try {
            // we only need the group's name to be returned here, all its member attributes
            // are read through a separate search
            return conn.search(_cfg.GROUP_BASE, _cfg.GROUP_SCOPE, anyGroup(), _cfg.GROUP_NAME);
        } finally {
            pool.releaseAndReAuthenticateConnection(conn);
        }
    }

    private Filter anyGroup()
    {
        List<Filter> groupClasses = Lists.newLinkedList();
        for (String groupClass : _cfg.GROUP_OBJECTCLASSES) {
            groupClasses.add(Filter.createEqualityFilter("objectClass", groupClass));
        }
        return Filter.createORFilter(groupClasses);
    }

    private Filter anyUser()
            throws LDAPException
    {
        Filter users = Filter.createEqualityFilter("objectClass", _cfg.USER_OBJECTCLASS);
        // we want to include the additional filter here so that users who fail that won't become
        // a pending group member (members who don't exist within AeroFS yet)
        if (!StringUtils.isNullOrEmpty(_cfg.USER_ADDITIONALFILTER)) {
            users = Filter.createANDFilter(users, Filter.create(_cfg.USER_ADDITIONALFILTER));
        }
        return users;
    }

    public static class AffectedUsersAndError
    {
        public final Boolean _errored;
        public final ImmutableCollection<UserID> _affected;

        public AffectedUsersAndError(Boolean errored, ImmutableCollection<UserID> affected)
        {
            _errored = errored;
            _affected = affected;
        }

        @Override
        public boolean equals(Object that)
        {
            return this == that || (that instanceof AffectedUsersAndError &&
                    _errored.equals(((AffectedUsersAndError)that)._errored) &&
                    _affected.equals(((AffectedUsersAndError)that)._affected));
        }

        @Override
        public int hashCode()
        {
            return _affected.hashCode() ^ _errored.hashCode();
        }
    }

    private static Logger _l = LoggerFactory.getLogger(LdapGroupSynchronizer.class);
    private Map<String, ImmutableSet<UserID>> coveredGroups;
    private ImmutableCollection.Builder<UserID> affected;
    private Organization _organization;
    private LdapConfiguration _cfg;
    private LdapAuthority _ldapAuthority;
    private LdapConnector _connector;
    private Group.Factory _groupFact;
    private User.Factory _userFact;
    private InvitationHelper _invitationHelper;
    private MessageDigest _md;
    private SQLThreadLocalTransaction _sqlTrans;
    private String[] _allMemberAttrs;
    private String[] _queryAttrs;
}
