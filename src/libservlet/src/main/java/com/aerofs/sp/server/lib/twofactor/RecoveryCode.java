/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.lib.twofactor;

import com.aerofs.proto.Sp.GetBackupCodesReply.BackupCode;
import com.google.common.base.Preconditions;

import javax.annotation.Nullable;
import java.util.Date;

/**
 * A text recovery code used for two-factor authentication.
 * Text codes may be consumed.
 */
public class RecoveryCode
{
    private String _code;
    private @Nullable Date _useDate;

    public RecoveryCode(String code, @Nullable Date useDate)
    {
        _code = code;
        _useDate = useDate;
    }

    public String code()
    {
        return _code;
    }

    public boolean isConsumed()
    {
        return _useDate != null;
    }

    public Date useDate()
    {
        Preconditions.checkState(_useDate != null);
        return _useDate;
    }

    public BackupCode toPB() {
        BackupCode.Builder codeBuilder = BackupCode.newBuilder();
        codeBuilder.setCode(_code);
        if (isConsumed()) {
            codeBuilder.setDateUsed(useDate().getTime());
        }
        return codeBuilder.build();
    }
}
