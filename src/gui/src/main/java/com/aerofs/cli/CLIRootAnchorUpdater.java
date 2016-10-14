/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.cli;

import com.aerofs.ids.SID;
import com.aerofs.labeling.L;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.S;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExNoConsole;
import com.aerofs.ui.SanityPoller.ShouldProceed;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.error.ErrorMessages;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIUtil;

import javax.annotation.Nullable;
import java.io.IOError;

public class CLIRootAnchorUpdater
{
    private CLI _cli;

    private final String _oldAbsPath;
    private final @Nullable SID _sid;

    private final String _relocateMsg;
    private final ShouldProceed _shouldProceed;
    // This string must be consistent with the string in CompRootAnchorUpdater
    // TODO (WW) define the string in S.java?
    private final String _unlinkOrQuitMsg = "If you want to move the missing folder " +
            "back to its original location, choose \"Quit,\" move the folder back to its " +
            "original location, and launch " + L.product() + " again.\n" +
            "If you deleted the " + L.product() + " folder, or want to start over, " +
            "choose \"Unlink.\" You will be asked to setup " + L.product() + " the next time " +
            L.product() + " launches.";

    public CLIRootAnchorUpdater(CLI cli, String oldAbsPath, @Nullable SID sid, ShouldProceed uc)
    {
        _cli = cli;
        _oldAbsPath = oldAbsPath;
        _sid = sid;
        _relocateMsg = relocateQuestion(oldAbsPath, sid);
        _shouldProceed = uc;
    }

    /**
     * Asks the user if they want to select a new root anchor path, unlink or quit the application.
     */
    public void ask()
    {
        try {
            _cli.out().print(_relocateMsg);
            if (_sid == null) {
                // The missing folder is the user's AeroFS folder. Relocate, Unlink, or Quit.
                if (_cli.ask(MessageType.INFO, "Would you like to select a new folder location?")) {
                    // if the location provided is not valid, reask the user to enter a new location
                    selectRootAnchorLocation();
                } else {
                    unlinkOrQuit();
                }
            } else if (L.isMultiuser()) {
                quitOrTryAgain();
            } else {
                leaveFolderOrTryAgain(_shouldProceed);
            }
        } catch (ExNoConsole exNoConsole) {
            _cli.show(MessageType.INFO, "Quit now.");
            _cli.shutdown();
        }
    }

    private static String folderDescription(@Nullable SID sid)
    {
        return sid == null
                ? "Your " + S.ROOT_ANCHOR
                : "One of your shared folders";
    }

    private static String relocateQuestion(String oldAbsPath, @Nullable SID sid)
    {
        return folderDescription(sid)
                + " was not found in the original location:\n" + oldAbsPath + ".\n";
    }

    /**
     * Ask the user to enter a new Root Anchor path.
     * If the path entered is invalid or an error occured while updating the DB ask the user
     * to enter a new path. Otherwise, stop the daemon.
     */
    private void selectRootAnchorLocation()
            throws ExNoConsole
    {
        Boolean isReplaced = replaceRootAnchor();
        if (isReplaced == null) {
            _cli.out().println();
            _cli.show(MessageType.WARN, S.NO_CONSOLE);
            throw new ExNoConsole();
        } else if (isReplaced) {
            UIGlobals.dm().stopIgnoreException();
        } else {
            selectRootAnchorLocation();
        }
    }

    /**
     * @return True if the rootAnchorAbsPath was updated successfully in the CfgDatabase, False
     * if the path entered is invalid or an error occurred while updating the DB. Null if there is
     * no console.
     */
    private Boolean replaceRootAnchor()
    {
        final OutArg<Boolean> ret = new OutArg<>();
        _cli.exec(() -> {
            _cli.out().print("Enter the new " + L.product() + " folder location: ");
            String rootPath = readLine();
            if (rootPath == null) return; // leave ret as null

            String newRootPath = RootAnchorUtil.adjustRootAnchor(rootPath, _sid);

            try {
                RootAnchorUtil.checkNewRootAnchor(_oldAbsPath, newRootPath);
            } catch (Exception e) {
                _cli.show(MessageType.WARN, ErrorMessages.e2msgDeprecated(e) +
                        ". Please select a different folder.\n");
                ret.set(false);
                return;
            }

            try {
                RootAnchorUtil.updateAbsRootCfg(_sid, newRootPath);
                Cfg.init_(Cfg.absRTRoot(), false);
                _cli.show(MessageType.INFO, L.product() +
                        "' new location was updated succesfully!");
                ret.set(true);
            } catch (Exception e) {
                _cli.show(MessageType.ERROR, "An error occured while applying " +
                        "the new location for the " + L.product() +
                        " folder " + ErrorMessages.e2msgDeprecated(e));
                ret.set(false);
            }
        });
        return ret.get();
    }

    /**
     * Asks the user whether they want to Quit the application and manually move the folder back
     * to its original location or if they want to Unlink this account from the computer.
     */
    private void unlinkOrQuit()
            throws ExNoConsole
    {
        if (_cli.ask(MessageType.INFO, _unlinkOrQuitMsg, "Quit", "Unlink")) {
            // Quit selected, shut down the application and let the user move the folder back
            _cli.shutdown();
        } else {
            try {
                // Unlink this account from the computer and shut down the application
                UIUtil.scheduleUnlinkAndExit();
            } catch (Exception e) {
                _cli.show(MessageType.ERROR, "Couldn't unlink the computer " + ErrorMessages.e2msgDeprecated(
                        e));
            }
        }
    }

    private void leaveFolderOrTryAgain(ShouldProceed shouldProceed) throws ExNoConsole
    {
        shouldProceed.proceedIf(
                _cli.ask(
                        MessageType.INFO,
                        "Would you like to unlink this folder on this device? If not, you must " +
                        "restore the folder on disk before continuing.",
                        "Yes",
                        "No"));
    }

    private void quitOrTryAgain() throws ExNoConsole
    {
        if (!_cli.ask(
                MessageType.INFO,
                "If you would like to move the folder back to its original location, " +
                "please do so and try again.\n" + L.product() + " cannot proceed until " +
                "the missing folder is restored.",
                "Try Again",
                "Quit")) {
            _cli.shutdown();
        }
    }

    /**
     * @return null on errors (e.g. console is closed)
     */
    private String readLine()
    {
        assert _cli.isUIThread();
        try {
            return System.console() == null ? null : System.console().readLine();
        } catch (IOError e) {
            return null;
        }
    }
}
