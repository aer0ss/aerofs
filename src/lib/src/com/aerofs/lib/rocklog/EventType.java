/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.rocklog;

public enum EventType {
    INSTALL_CLIENT("Install Single User Client"),
    INSTALL_TEAM_SERVER("Install Team Server"),
    FOLDER_INVITE_SENT("Folder Invite Sent"),
    SIGNUP_INVITE_SENT("Signup Invite Sent"),
    REINSTALL_CLIENT("Reinstall Single User Client"),
    ENABLE_S3("Enable S3"),
    FILE_SAVED("File Saved"),
    FILE_CONFLICT("File Conflict");

    private final String _name;

    EventType(final String name)
    {
        _name = name;
    }

    @Override
    public String toString()
    {
        return _name;
    }
}