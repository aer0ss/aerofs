package com.aerofs.base.acl;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.id.GroupID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.UserID;
import com.aerofs.proto.Common.PBSubjectPermissions;

import javax.annotation.Nullable;

/**
 * A subject is an entity that has a set of permissions associated with it in the context of ACL.
 * In the past, all subjects are users and are identified by an UserID (including the Team Server
 * user). Since then, we've extended subjects to also include groups to support group-sharing.
 *
 * Due to constraints placed by SP/ritual protocols, all subjects must have _unique_ string
 * representations and must be convertible to and from their string representations. See
 * {@link #getStringFromSubject(Object)} and {@link #getSubjectFromString(String)}.
 */
public final class SubjectPermissions
{
    // N.B. a ':' may only appear in an email address if it is quoted, hence "g:' is not a prefix
    //   for any valid email address nor is it a valid prefix for Team Server users' UserIDs.
    private static final String GROUP_PREFIX = "g:";

    // In the context of ACL, we care more about the subject/permissions pairing more than what
    // constitutes a valid subject. After considering other alternatives, I chose this approach
    // because it involves the fewest layers.
    public final Object _subject;
    public final Permissions _permissions;
    @Nullable private PBSubjectPermissions _pb;

    public SubjectPermissions(UserID userID, Permissions permissions)
    {
        this(userID, permissions, null);
    }

    public SubjectPermissions(GroupID groupID, Permissions permissions)
    {
        this(groupID, permissions, null);
    }

    private SubjectPermissions(Object subject, Permissions permissions,
            @Nullable PBSubjectPermissions pb)
    {
        this._subject = subject;
        this._permissions = permissions;
        this._pb = pb;
    }

    public PBSubjectPermissions toPB()
    {
        if (_pb == null) {
            _pb = PBSubjectPermissions.newBuilder()
                    .setSubject(getStringFromSubject())
                    .setPermissions(_permissions.toPB())
                    .build();
        }

        return _pb;
    }

    public static SubjectPermissions fromPB(PBSubjectPermissions pb)
            throws ExBadArgs
    {
        return new SubjectPermissions(
                getSubjectFromString(pb.getSubject()),
                Permissions.fromPB(pb.getPermissions()), pb);
    }

    @Override
    public String toString()
    {
        return _subject + ": " + _permissions;
    }

    private String getStringFromSubject()
    {
        return getStringFromSubject(_subject);
    }

    public static String getStringFromSubject(Object subject)
    {
        if (subject instanceof GroupID) {
            return GROUP_PREFIX + Integer.toString(((GroupID)subject).getInt());
        } else if (subject instanceof UserID) {
            return ((UserID)subject).getString();
        } else {
            throw new IllegalArgumentException(subject + " is not a valid subject.");
        }
    }

    public static Object getSubjectFromString(String subject)
            throws ExBadArgs
    {
        return isGroupSubject(subject)
                ? getGroupIDFromString(subject)
                : getUserIDFromString(subject);
    }

    private static boolean isGroupSubject(String subject)
    {
        return subject.startsWith(GROUP_PREFIX);
    }

    public static GroupID getGroupIDFromString(String subject)
            throws ExBadArgs
    {
        if (isGroupSubject(subject)) {
            try {
                // N.B. Need a parseLong here instead of a parseInt because groupID is a uint32 type
                // in protobuf. Normally protobuf takes care of converting that back into an int,
                // but since we've passed it around as a string if this message comes from a
                // language that supports uint32 (e.g. Python), the string value can be larger than
                // the max integer value
                return GroupID.fromExternal((int)Long.parseLong(subject.substring(GROUP_PREFIX.length())));
            } catch (NumberFormatException e) {
                // falls-through to throw ExBadArgs
            }
        }

        throw new ExBadArgs(subject + " is not a valid group subject.");
    }

    public static UserID getUserIDFromString(String subject)
            throws ExBadArgs
    {
        try {
            return UserID.fromExternal(subject);
        } catch (ExInvalidID e) {
            throw new ExBadArgs("The e-mail address of an user subject cannot be empty.");
        }
    }
}
