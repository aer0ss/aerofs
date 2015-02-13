/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.analytics;

import com.aerofs.ids.UserID;

import java.util.Map;

/**
 * This class encapsulates all the analytics events that we use in one single location
 */
public class AnalyticsEvents
{
    /**
     * Simple events are events that have a name but no additional properties (only the default
     * properties that are sent with all events).
     *
     * If you're adding a new analytics event that doesn't need additional properties, add it to
     * this enum. If, on the other hand, you need additional properties, then make a new event
     * class. (see below)
     */
    public enum SimpleEvents implements IAnalyticsEvent
    {
        INSTALL_CLIENT("Install Single User Client"),
        INSTALL_TEAM_SERVER("Install Team Server"),
        UNLINK_AND_WIPE("Unlink And Wipe Device"),
        UNLINK_DEVICE("Unlink Device"),
        REINSTALL_CLIENT("Reinstall Single User Client"),
        ENABLE_S3("Enable S3"),
        SIGN_IN("Sign In"),
        EXCLUDE_FOLDER("Exclude Folder"),       // exclude a folder from sync
        INCLUDE_FOLDER("Include Folder"),       // readmit an excluded folder
        KICKOUT("Kickout"),                     // kicked-out from a shared folder

        // Mobile app events
        LISTED_ROOT("Listed Root"),
        LISTING_ROOT_FAILED("Listing Root Failed"),
        DOWNLOADED_FILE("Downloaded File"),
        ;

        private final String _name;

        SimpleEvents(String name)
        {
            _name = name;
        }

        @Override
        public String getName()
        {
            return _name;
        }

        @Override
        public String toString()
        {
            return _name;
        }

        @Override
        public void saveProperties(Map<String, String> properties)
        {
            // simple events have no properties to save
        }
    }


    //
    // Custom events that define additional properties
    // If you want to create an analytics event with additional properties, add it as a class below
    //


    /**
     * Base class for events that define a "count" property.
     */
    private static abstract class CountableEvent implements IAnalyticsEvent
    {
        private final String _name;
        private final int _count;

        CountableEvent(String name, int count)
        {
            _name = name;
            _count = count;
        }

        @Override
        public String getName()
        {
            return _name;
        }

        @Override
        public void saveProperties(Map<String, String> properties)
        {
            properties.put("count", Integer.toString(_count));
        }
    }

    public static class FolderInviteSentEvent extends CountableEvent
    {
        public FolderInviteSentEvent(int count)
        {
            super("Folder Invite Sent", count);
        }
    }

    public static class SignupInviteSentEvent extends CountableEvent
    {
        public SignupInviteSentEvent(int count)
        {
            super("Signup Invite Sent", count);
        }
    }

    public static class FileConflictEvent extends CountableEvent
    {
        public FileConflictEvent(int count)
        {
            super("File Conflict", count);
        }
    }

    public static class FileSavedEvent extends CountableEvent
    {
        public FileSavedEvent(int count)
        {
            super("File Saved", count);
        }
    }

    public static class SignUpEvent implements IAnalyticsEvent
    {
        private final UserID _userId;

        public SignUpEvent(UserID userId)
        {
            _userId = userId;
        }

        @Override
        public String getName()
        {
            return "Sign Up";
        }

        @Override
        public void saveProperties(Map<String, String> properties)
        {
            properties.put("id", _userId.getString());
        }
    }

    public static class UpdateEvent implements IAnalyticsEvent
    {
        private final String _oldVersion;

        public UpdateEvent(String oldVersion)
        {
            _oldVersion = oldVersion;
        }

        @Override
        public String getName()
        {
            return "Update";
        }

        @Override
        public void saveProperties(Map<String, String> properties)
        {
            properties.put("old_version", _oldVersion);
        }
    }

    /**
     * Click event. Those are sent when the user clicks on a variety of places in the UI
     */
    public static class ClickEvent implements IAnalyticsEvent
    {
        /**
         * Action that the click is performing
         */
        public enum Action
        {
            TRAY_ICON("Tray Icon"),
            TRAY_ICON_DEFAULT_ACTION("Tray Icon Default Action"),
            OPEN_AEROFS_FOLDER("Open AeroFS Folder"),
            MANAGE_SHARED_FOLDER("Manage Shared Folder"),
            PREFERENCES("Preferences"),
            EXIT("Exit"),
            DEFAULT_SELECTION("Default Selection"),
            APPLY_UPDATE("Apply Update"),
            PAUSE_SYNCING("Pause Syncing"),
            RESUME_SYNCING("Resume Syncing"),
            WHY_NOT_SYNCED("Why Not Synced"),
            RESOLVE_CONFLICTS("Resolve Conflicts"),
            MANAGE_ORGANIZATION("Manage Organization"),
            RESOLVE_UNSYNCABLE_FILES("Resolve Unsyncable Files"),
            INVITE_COWORKER_MENU("Invite Coworker Menu"),
            INVITE_COWORKER("Invite Coworker"),
            INVITE_COWORKER_SUCCEEDED("Invite Coworker Succeeded"),
            INVITE_COWORKER_FAILED("Invite Coworker Failed"),
            ;

            private final String _name;
            Action(String name) { _name = name; }
            @Override public String toString() { return _name; }
        }

        /**
         * Where the click originated
         * ie: taskbar menu, shell extension, etc
         */
        public enum Source
        {
            TASKBAR("taskbar"),
            DESKTOP_GUI("desktop_gui"),
            // SHELLEXT("shellext"),  // <-- not used yet, just as an example
            ;

            private final String _name;
            Source(String name) { _name = name; }
            @Override public String toString() { return _name; }
        }

        private final Action _action;
        private final Source _source;

        /**
         * Created a click event. Those events are created when the user clicks on various elements
         * in the GUI
         * @param action The action being performed by the click
         * @param source Where the click originated (since the same action can be triggered from
         * several places)
         */
        public ClickEvent(Action action, Source source)
        {
            _action = action;
            _source = source;
        }

        @Override
        public String getName()
        {
            return "Click";
        }

        @Override
        public void saveProperties(Map<String, String> properties)
        {
            properties.put("action", _action.toString());
            properties.put("source", _source.toString());
        }
    }
}
