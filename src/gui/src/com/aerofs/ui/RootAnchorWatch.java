/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.ui;

import com.aerofs.gui.shellext.ShellextService;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ex.ExNoConsole;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.injectable.InjectableJNotify;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.ui.IUI.MessageType;
import net.contentobjects.jnotify.JNotify;
import net.contentobjects.jnotify.JNotifyException;
import net.contentobjects.jnotify.JNotifyListener;
import org.apache.log4j.Logger;

import java.io.IOException;

public class RootAnchorWatch implements JNotifyListener
{
    private static final Logger l = Util.l(RootAnchorWatch.class);

    private final InjectableFile.Factory _factFile;
    private final InjectableJNotify _jn;

    private final int _mask = JNotify.FILE_DELETED | JNotify.FILE_RENAMED;
    private int _watchID;
    private String _oldRoot;

    public RootAnchorWatch(InjectableFile.Factory factFile, InjectableJNotify jn)
    {
        _factFile = factFile;
        _jn = jn;
    }

    public void start() throws JNotifyException
    {
        // TODO (KH): Change this to Cfg.useS3() when available
        // defaultValue of S3_DIR is null
        if (Cfg.db().getNullable(Key.S3_DIR) != null) {
            return;
        }

        _oldRoot = Cfg.absRootAnchor();
        InjectableFile rootAnchor = _factFile.create(_oldRoot);

        if (!rootAnchor.isDirectory()) {
            notifyMissingRootAnchor();
        }

        // There is a minor gap between checking for existence of root anchor and registering
        // the watch on the parent folder. However, this is small and placing the check first
        // will also check for parent directory existence.

        _watchID = _jn.addWatch(rootAnchor.getParent(), _mask, false, this);
    }

    @Override
    public void fileRenamed(int wd, String root, String from, String to)
    {
        checkRootAnchor(from);
    }

    @Override
    public void fileModified(int wd, String root, String name)
    {
        assert false;
    }

    @Override
    public void fileDeleted(int wd, String root, String name)
    {
        checkRootAnchor(name);
    }

    @Override
    public void fileCreated(int wd, String root, String name)
    {
        assert false;
    }

    private void checkRootAnchor(String file)
    {
        if (file.equals(S.ROOT_ANCHOR_NAME)) {
            notifyMissingRootAnchor();
        }
    }

    /**
     * This method should never return
     *
     * JNotify will block when calling this method, only showing the dialogue once.
     */
    private void notifyMissingRootAnchor()
    {
        blockingRitualCall();

        if (onPotentialRootAnchorChange()) return;

        try {
            // Quit is the yesLabel which is highlighted which makes it seem more like the default
            // option.
            if (!UI.get().ask(MessageType.ERROR, "Your " + S.PRODUCT + " folder has been " +
                    "moved or deleted from its original location, " + Cfg.absRootAnchor() + ". "
                    + S.PRODUCT + " will not work properly until the folder is moved back" +
                    ".\n\n" + "Please quit " + S.PRODUCT + ", move the folder back to" +
                    " its original location, and launch " + S.PRODUCT + " again.\n\n" +
                    "To move the folder properly without causing this dialog to" +
                    " show up, use the " + S.PRODUCT + " Preferences dialog.", "Quit",
                    "Unlink this computer") &&
                    UI.get().ask(MessageType.WARN, S.UNLINK_THIS_COMPUTER_CONFIRM)) {

                try {
                    UIUtil.unlinkAndExit(_factFile);
                } catch (Exception e) {
                    UI.get().show(MessageType.ERROR, "Couldn't unlink the computer "
                            + UIUtil.e2msg(e));
                }
            }
        } catch (ExNoConsole e) {
            UI.get().show(MessageType.INFO, "Quit now.");
        } finally {
            UI.get().shutdown();
            System.exit(0);
        }
    }

    /**
     * Call this method if the root anchor is possibly changed. It does everything necessary to
     * react to the change. Note that this method can be called from UI or non-UI threads
     * @return whether the root anchor has actually changed.
     */
    public synchronized boolean onPotentialRootAnchorChange()
    {
        // The user might have changed the root anchor from other processes so we need to reload the
        // configuration.
        try {
            Cfg.init_(Cfg.absRTRoot(), false);
        } catch (Exception e) {
            // Exception is ignored because handling of Cfg init exception at this level is hard.
            // Also, the caller is the JNotify method which cannot handle it properly.
            // There might be false positives because of this.
            l.warn("reload cfg. ignored: " + Util.e(e));
        }

        InjectableFile rootAnchor = _factFile.create(Cfg.absRootAnchor());
        if (!rootAnchor.isDirectory()) return false;

        ShellextService.get().notifyRootAnchor();

        String newRoot = Cfg.absRootAnchor();
        try {
            OSUtil.get().remFromFavorite(_oldRoot);
            OSUtil.get().addToFavorite(newRoot);
        } catch (IOException e) {
            l.warn("Updating favorites failed: " + e);
        }
        _oldRoot = newRoot;

        try {
            _jn.removeWatch(_watchID);
            _watchID = _jn.addWatch(rootAnchor.getParent(), _mask, false, this);
        } catch (JNotifyException e) {
            l.warn("Reregistering watch on root anchor failed: " + e);
        }

        return true;
    }

    /*
     * An arbitrary rpc to block until the daemon finishes. This prevents unnecessary dialogues
     * showing when relocation of root anchor is in progress.
     */
    private void blockingRitualCall()
    {
        RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
        try {
            ritual.heartbeat();
        } catch (Exception e) {
            l.warn("Rpc call failure ignored: " + Util.e(e, Exception.class));
        } finally {
            ritual.close();
        }
    }
}
