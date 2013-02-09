/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sv.common;

/**
 * The email Category for the given email (e.g. a password reset email, an invite email,
 * a spuport email, etc...)
 *
 * Every new email type we create should usually have its own distinct category
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
    REQUEST_TO_SIGN_UP_AND_ACTIVATE_BUSINESS_PLAN,
    REQUEST_TO_ACTIVATE_BUSINESS_PLAN
}
