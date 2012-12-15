/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.cli;

import com.aerofs.labeling.L;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.S;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ex.ExNoConsole;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIUtil;

import java.io.IOError;

public class CLIRootAnchorUpdater
{
    private CLI _cli;
    private final String _relocateMsg = "Your " + L.PRODUCT + " folder was not found in" +
            " the original location:\n" + Cfg.absRootAnchor() + ".\n" +
            "Would you like to select a new folder location?";

    private final String _unlinkOrQuitMsg = "If you want to move the " + L.PRODUCT + " folder " +
            "back to its original location, choose \"Quit\", move it back to its original" +
            " location, and launch " + L.PRODUCT + " again.\n" +
            "If you deleted the " + L.PRODUCT + " folder, or want to start over, " +
            "choose \"Unlink\". You will be asked to setup AeroFS the next time you launch.";

    private final InjectableFile.Factory _factFile = new InjectableFile.Factory();

    public CLIRootAnchorUpdater(CLI cli)
    {
        _cli = cli;
    }

    /**
     * Asks the user if they want to select a new root anchor path, unlink or quit the application.
     */
    public void ask()
    {
        try {
            if (_cli.ask(MessageType.INFO, _relocateMsg)) {
                // if the location provided is not valid, reask the user to enter a new location
                selectRootAnchorLocation();
            } else {
                unlinkOrQuit();
            }
        } catch (ExNoConsole exNoConsole) {
            _cli.show(MessageType.INFO, "Quit now.");
            _cli.shutdown();
            System.exit(0);
        }
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
            _cli.shutdown();
        } else {
            selectRootAnchorLocation();
        }
    }

    /**
     * @return True if the rootAnchorAbsPath was updated successfully in the CfgDatabase, False
     * if the path entered is invalid or an error occured while updating the DB. Null if there is
     * no console.
     */
    private Boolean replaceRootAnchor()
    {
        final OutArg<Boolean> ret = new OutArg<Boolean>();
        _cli.exec(new Runnable()
        {
            @Override
            public void run()
            {
                _cli.out().print("Enter the new " + L.PRODUCT + " folder location: ");
                String rootPath = readLine();
                if (rootPath == null) return; // leave ret as null

                String oldRootPath = Cfg.absRootAnchor();
                String newRootPath = RootAnchorUtil.adjustRootAnchor(rootPath);

                try {
                    RootAnchorUtil.checkNewRootAnchor(oldRootPath, newRootPath);
                } catch (Exception e) {
                    _cli.show(MessageType.WARN, UIUtil.e2msg(e) +
                            ". Please select a different folder.\n");
                    ret.set(false);
                    return;
                }

                try {
                    Cfg.db().set(Key.ROOT, newRootPath);
                    Cfg.init_(Cfg.absRTRoot(), false);
                    _cli.show(MessageType.INFO, L.PRODUCT +
                            "' new location was updated succesfully!");
                    ret.set(true);
                } catch (Exception e) {
                    _cli.show(MessageType.ERROR, "An error occured while applying " +
                            "the new location for the " + L.PRODUCT +
                            " folder " + UIUtil.e2msg(e));
                    ret.set(false);
                }
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
            System.exit(0);
        } else {
            try {
                // Unlink this account from the computer and shut down the application
                UIUtil.unlinkAndExit(_factFile);
            } catch (Exception e) {
                _cli.show(MessageType.ERROR, "Couldn't unlink the computer " + UIUtil.e2msg(e));
            }
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
