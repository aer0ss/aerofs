/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.sharing;

import com.aerofs.base.id.UserID;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.FullName;
import com.aerofs.lib.ex.sharing_rules.AbstractExSharingRules.DetailedDescription;
import com.aerofs.lib.ex.sharing_rules.ExSharingRulesError;
import com.aerofs.lib.ex.sharing_rules.ExSharingRulesWarning;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.error.ErrorMessage;
import com.aerofs.ui.error.ErrorMessages;
import org.eclipse.swt.widgets.Shell;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Map.Entry;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * This class is an utility class for individual UI exception handlers
 * to handle sharing rules exceptions.
 */
public class SharingRulesExceptionHandlers
{
    private SharingRulesExceptionHandlers() { } // prevents instantiation

    /**
     * @return true iff this set of handlers can handle {@paramref e}
     */
    public static boolean canHandle(Exception e)
    {
        return e instanceof ExSharingRulesError || e instanceof ExSharingRulesWarning;
    }

    /**
     * @pre {@code canHandle(e)} is true
     * @return true iff we should suppress the warning and retry the operation.
     */
    public static boolean promptUserToSuppressWarning(Shell shell, Exception e)
    {
        checkArgument(canHandle(e));
        if (e instanceof ExSharingRulesError) {
            return handleError(shell, (ExSharingRulesError)e);
        } else if (e instanceof ExSharingRulesWarning) {
            return handleWarning(shell, (ExSharingRulesWarning)e);
        } else {
            checkArgument(false);
            return false; // should not be reachable
        }
    }

    /**
     * @return a non-null, but possibly empty string.
     */
    private static @Nonnull String formatUsers(Map<UserID, FullName> users)
    {
        if (users == null) return "";

        StringBuilder builder = new StringBuilder();

        for (Entry<UserID, FullName> user : users.entrySet()) {
            builder.append(GUIUtil.BULLET).append(' ')
                    .append(user.getValue().getString())
                    .append(" <")
                    .append(user.getKey().getString())
                    .append(">\n");
        }

        return builder.toString();
    }

    private static boolean handleError(Shell shell, ExSharingRulesError e)
    {
        ErrorMessages.show(shell, e, "Unused Default.",
                new ErrorMessage(e.getClass(),
                        e.description().description.replace("{}", formatUsers(e.description().users))));

        return false;
    }

    private static boolean handleWarning(Shell shell, ExSharingRulesWarning e)
    {
        for (DetailedDescription d : e.descriptions()) {
            boolean proceed = GUI.get().askWithDefaultOnNoButton(shell, MessageType.WARN,
                    d.description.replace("{}", formatUsers(d.users)),
                    "Proceed", "Cancel");
            if (!proceed) return false;
        }
        return true;
    }
}
