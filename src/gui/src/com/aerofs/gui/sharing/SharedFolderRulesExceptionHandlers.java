/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.sharing;

import com.aerofs.base.id.UserID;
import com.aerofs.gui.GUI;
import com.aerofs.lib.FullName;
import com.aerofs.lib.ex.shared_folder_rules.ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders;
import com.aerofs.lib.ex.shared_folder_rules.ExSharedFolderRulesWarningAddExternalUser;
import com.aerofs.lib.ex.shared_folder_rules.ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.error.ErrorMessage;
import com.aerofs.ui.error.ErrorMessages;
import com.google.common.collect.ImmutableMap;
import org.eclipse.swt.widgets.Shell;

import javax.annotation.Nonnull;
import java.util.Map.Entry;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * This class is an utility class for individual GUI exception handlers
 * to handle exceptions from enforcing shared folder rules.
 */
public class SharedFolderRulesExceptionHandlers
{
    private SharedFolderRulesExceptionHandlers() { } // prevents instantiation

    /**
     * @return true iff this set of handlers can handle {@paramref e}
     */
    public static boolean canHandle(Exception e)
    {
        return e instanceof ExSharedFolderRulesWarningAddExternalUser
                || e instanceof ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders
                || e instanceof ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers;
    }

    /**
     * @pre {@code canHandle(e)} is true
     * @return true iff we should suppress the warning and retry the operation.
     */
    public static boolean promptUserToSuppressWarning(Shell shell, Exception e)
    {
        checkArgument(canHandle(e));

        if (e instanceof ExSharedFolderRulesWarningAddExternalUser) {
            return handleAddExternalUser(shell);
        } else if (e instanceof ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders) {
            return handleEditorsDisallowedInExternallySharedFolders(shell, e);
        } else if (e instanceof ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers) {
            return handleOwnerCanShareWithExternalUsers(shell, e);
        } else {
            checkState(false);
            return false; // should not be reachable
        }
    }

    /**
     * @return a non-null, but possibly empty string.
     */
    private static @Nonnull String formatUsers(ImmutableMap <UserID, FullName> users)
    {
        if (users == null) return "";

        StringBuilder builder = new StringBuilder();

        for (Entry<UserID, FullName> user : users.entrySet()) {
            // \u25CF is the unicode character for black circle. It is used as a bullet.
            builder.append("\u25CF ")
                    .append(user.getValue().getString())
                    .append(" <")
                    .append(user.getKey().getString())
                    .append(">\n");
        }

        return builder.toString();
    }

    private static boolean handleAddExternalUser(Shell shell)
    {
        // N.B. this message _must_ match the message on web GUI at
        // src/web/web/views/shared_folders/templates/shared_folder_modals.mako
        String message = "You are about to share this folder with external users.\n\n" +
                "Editors of this folder will be automatically converted to Viewers.\n\n" +
                "Please ensure that this folder contains no confidential material before " +
                "proceeding.";

        return GUI.get().askWithDefaultOnNoButton(shell, MessageType.WARN, message,
                "Confirm", "Cancel");
    }

    private static boolean handleEditorsDisallowedInExternallySharedFolders(Shell shell, Exception e)
    {
        ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders ex =
                (ExSharedFolderRulesEditorsDisallowedInExternallySharedFolders) e;

        // N.B. this message _must_ match the message on web GUI at
        // src/web/web/views/shared_folders/templates/shared_folder_modals.mako
        String message = "Editors are not allowed in folders shared with external users. This " +
                "folder is shared with the following external users:\n\n" +
                formatUsers(ex.getExternalUsers()) + "\n" +
                "Please reinvite the new user as Viewer instead.";

        ErrorMessages.show(shell, ex, "Unused Default.", new ErrorMessage(ex.getClass(), message));

        return false;
    }

    private static boolean handleOwnerCanShareWithExternalUsers(Shell shell, Exception e)
    {
        ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers ex =
                (ExSharedFolderRulesWarningOwnerCanShareWithExternalUsers) e;

        // N.B. this message _must_ match the message on web GUI at
        // src/web/web/views/shared_folders/templates/shared_folder_modals.mako
        String message = "You are adding a new Owner to this folder, which is shared with " +
                "the following external users:\n\n" +
                formatUsers(ex.getExternalUsers()) + "\n" +
                "Please advise the Owner to be mindful and not to place confidential " +
                "material into this folder.";

        return GUI.get().askWithDefaultOnNoButton(shell, MessageType.WARN, message,
                "Confirm", "Cancel");
    }
}
