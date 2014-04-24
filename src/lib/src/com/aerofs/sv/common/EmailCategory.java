/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sv.common;

/**
 * The email category for the given email (e.g. a password reset email, an invite email, a suport
 * email, etc...). It is used by Sendgrid for analytics purposes.
 *
 * Every new email type we create should have its own distinct category. To create a new category,
 * simply add a new enum value here and you're all set.
 */
public enum EmailCategory
{
    AEROFS_INVITATION_REMINDER,
    FOLDERLESS_INVITE,
    FOLDER_INVITE,
    PASSWORD_RESET,
    SUPPORT,
    ORGANIZATION_INVITATION,
    DEVICE_CERTIFIED,
    REQUEST_TO_SIGN_UP,
    SHARED_FOLDER_INVITATION_ACCEPTED_NOTIFICATION,
    SHARED_FOLDER_ROLE_CHANGE_NOTIFICATION,
    SMTP_VERIFICATION,
    QUOTA_WARNING,
}
