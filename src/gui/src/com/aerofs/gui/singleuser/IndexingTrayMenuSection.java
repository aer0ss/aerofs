/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.singleuser;

import com.aerofs.gui.AbstractSpinAnimator;
import com.aerofs.gui.GUI;
import com.aerofs.gui.singleuser.IndexingPoller.IIndexingCompletionListener;
import com.aerofs.gui.tray.TrayMenuPopulator;
import com.aerofs.proto.RitualNotifications.PBIndexingProgress;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.ui.RitualNotificationClient.IListener;
import com.aerofs.ui.UI;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

public class IndexingTrayMenuSection implements IListener, IIndexingCompletionListener
{
    private final Menu _menu;
    private MenuItem _indexingStats1;
    private MenuItem _indexingStats2;

    private final IndexingPoller _indexingPoller;
    private final TrayMenuPopulator _trayMenuPopulator;

    private AbstractSpinAnimator _animator;

    public IndexingTrayMenuSection(Menu menu, TrayMenuPopulator trayMenuPopulator,
            IndexingPoller indexingPoller)
    {
        _menu = menu;
        _indexingPoller = indexingPoller;
        _trayMenuPopulator = trayMenuPopulator;

        UI.rnc().addListener(this);
    }

    public void populate()
    {
        _indexingPoller.addListener(this);

        _indexingStats1 = _trayMenuPopulator.addMenuItem("Indexing your files...", null);
        _indexingStats1.setEnabled(false);

        _indexingStats2 = null;

        _animator = new AbstractSpinAnimator(_indexingStats1) {
            @Override
            protected void setImage(Image img)
            {
                _indexingStats1.setImage(img);
            }
        };

        _animator.start();
    }

    private void updateProgress(PBIndexingProgress progress)
    {
        String lbl = progress.getFiles() + " files in " + progress.getFolders() + " folders";
        if (_indexingStats2 == null) {
            _indexingStats2 = _trayMenuPopulator.addMenuItemAfterItem(lbl, _indexingStats1, null);
            _indexingStats2.setEnabled(false);
        } else {
            _indexingStats2.setText(lbl);
        }
    }

    @Override
    public void onNotificationReceived(final PBNotification pb)
    {
        if (pb.getType() == Type.INDEXING_PROGRESS) {
            GUI.get().safeAsyncExec(_menu, new Runnable() {
                @Override
                public void run()
                {
                    if (_indexingStats1 != null) updateProgress(pb.getIndexingProgress());
                }
            });
        }
    }

    @Override
    public void onIndexingDone()
    {
        // stop the spinner before we refresh the menu
        if (_animator != null) {
            _animator.stop();
            _animator = null;
        }

        GUI.get().safeAsyncExec(_menu, new Runnable() {
            @Override
            public void run()
            {
                // force a menu refresh
                if (_menu.isVisible()) {
                    _indexingStats1 = null;
                    _menu.setVisible(false);
                    _menu.setVisible(true);
                }
            }
        });
    }
}
