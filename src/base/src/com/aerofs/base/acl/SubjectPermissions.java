package com.aerofs.base.acl;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Common.PBSubjectPermissions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class SubjectPermissions
{
    public final UserID _subject;
    public final Permissions _permissions;
    @Nullable private PBSubjectPermissions _pb;

    public SubjectPermissions(@Nonnull PBSubjectPermissions pb)
            throws ExBadArgs, ExEmptyEmailAddress
    {
        this._permissions = Permissions.fromPB(pb.getPermissions());
        this._subject = UserID.fromExternal(pb.getSubject());
        this._pb = pb;
    }

    public SubjectPermissions(UserID subject, Permissions permissions)
    {
        this._subject = subject;
        this._permissions = permissions;
    }

    public PBSubjectPermissions toPB()
    {
        if (_pb == null) {
            _pb = PBSubjectPermissions.newBuilder()
                    .setSubject(_subject.getString())
                    .setPermissions(_permissions.toPB())
                    .build();
        }

        return _pb;
    }

    @Override
    public String toString()
    {
        return _subject + ": " + _permissions;
    }
}
