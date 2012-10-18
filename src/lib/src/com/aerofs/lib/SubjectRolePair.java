package com.aerofs.lib;

import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.proto.Common.PBSubjectRolePair;

import javax.annotation.Nullable;

public class SubjectRolePair
{
    public final String _subject;
    public final Role _role;
    @Nullable private PBSubjectRolePair _pb;

    public SubjectRolePair(PBSubjectRolePair pb) throws ExBadArgs
    {
        this._role = Role.fromPB(pb.getRole());
        this._subject = pb.getSubject();
        this._pb = pb;
    }

    public SubjectRolePair(String subject, Role role)
    {
        this._subject = subject;
        this._role = role;
    }

    public PBSubjectRolePair toPB()
    {
        if (_pb == null) {
            _pb = PBSubjectRolePair.newBuilder()
                    .setSubject(_subject)
                    .setRole(_role.toPB())
                    .build();
        }

        return _pb;
    }
}
