/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.acl.Permissions;
import com.aerofs.ids.UserID;
import com.aerofs.lib.LibParam.Identity;
import com.aerofs.lib.Util;
import com.aerofs.sp.server.lib.SPParam;
import com.google.common.base.Strings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This class isolates variations of email contents from the caller which composes fixed parts of
 * the content.
 */
class InvitationEmailContentStrategy
{
    @Nonnull private final UserID _invitee;
    @Nullable private final String _folderName;
    @Nullable private final Permissions _permissions;
    @Nullable private final String _note;
    @Nullable private final String _groupName;
    @Nullable private final String _signUpCode;

    /**
     * _folderName will be null if this invitation isn't associated with a folder, _groupName
     * will be null if not associated with a group, if both are null then it's a plain invitation
     */
    InvitationEmailContentStrategy(@Nonnull UserID invitee, @Nullable String folderName,
            @Nullable Permissions permissions, @Nullable String note, @Nullable String groupName,
            @Nullable String signUpCode)
    {
        _invitee = invitee;
        _folderName = folderName;
        _permissions = permissions;
        _note = note;
        _groupName = groupName;
        _signUpCode = signUpCode;
    }

    String subject()
    {
        if (isFolderInvite()) {
            return "Join My " + SPParam.BRAND + " Folder";
        } else if (isGroupInvite()) {
            return "Join My " + SPParam.BRAND + " Group";
        } else {
            return "Invitation to " + SPParam.BRAND;
        }
    }

    String invitedTo()
    {
        if (isFolderInvite()) {
            assert _permissions != null;
            return "a shared " + SPParam.BRAND + " folder " + Util.quote(_folderName) + " as " +
                    _permissions.roleName();
        } else if (isGroupInvite()) {
            return "the " + SPParam.BRAND + " group " + Util.quote(_groupName);
        } else {
            return SPParam.BRAND;
        }
    }

    String noteAndEndOfSentence()
    {
        if (isNoteEmpty(_note)) return ".";
        else return ":\n\n" + _note;
    }

    String signUpURLAndInstruction()
    {
        if (isAutoProvisioned()) {
            return RequestToSignUpEmailer.DASHBOARD_HOME + "\n" +
                    "\n" +
                    "When prompted, please use your " + Identity.SERVICE_IDENTIFIER + " account (" +
                    _invitee.getString() + ") to log in.";
        } else {
            return RequestToSignUpEmailer.getSignUpLink(_signUpCode);
        }
    }

    private boolean isAutoProvisioned()
    {
        return _signUpCode == null;
    }

    private boolean isNoteEmpty(@Nullable String note)
    {
        return Strings.nullToEmpty(note).trim().isEmpty();
    }

    private boolean isFolderInvite()
    {
        return _folderName != null;
    }

    private boolean isGroupInvite()
    {
        return _groupName != null;
    }
}
