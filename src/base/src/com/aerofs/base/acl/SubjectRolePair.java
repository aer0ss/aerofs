package com.aerofs.base.acl;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Common.PBSubjectRolePair;

import javax.annotation.Nullable;

public final class SubjectRolePair
{
    public final UserID _subject;
    public final Role _role;
    @Nullable private PBSubjectRolePair _pb;

    public SubjectRolePair(PBSubjectRolePair pb) throws ExBadArgs, ExEmptyEmailAddress
    {
        this._role = Role.fromPB(pb.getRole());
        this._subject = UserID.fromExternal(pb.getSubject());
        this._pb = pb;
    }

    public SubjectRolePair(UserID subject, Role role)
    {
        this._subject = subject;
        this._role = role;
    }

    public PBSubjectRolePair toPB()
    {
        if (_pb == null) {
            _pb = PBSubjectRolePair.newBuilder()
                    .setSubject(_subject.getString())
                    .setRole(_role.toPB())
                    .build();
        }

        return _pb;
    }

    @Override
    public String toString()
    {
        return _subject + ": " + _role;
    }
}
