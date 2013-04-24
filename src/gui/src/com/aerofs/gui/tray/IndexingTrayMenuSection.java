/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.tray;

import com.aerofs.base.Loggers;
import com.aerofs.gui.AbstractSpinAnimator;
import com.aerofs.gui.GUI;
import com.aerofs.gui.tray.IndexingPoller.IIndexingCompletionListener;
import com.aerofs.proto.RitualNotifications.PBIndexingProgress;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.ui.RitualNotificationClient.IListener;
import com.aerofs.ui.UI;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.slf4j.Logger;

import java.util.List;

public class IndexingTrayMenuSection
        implements IListener, IIndexingCompletionListener, ITrayMenuComponent
{
    private static Logger l = Loggers.getLogger(IndexingTrayMenuSection.class);
    private MenuItem _indexingStats1;
    private MenuItem _indexingStats2;
    private Menu _cachedMenu;

    private final IndexingPoller _indexingPoller;
    private final List<ITrayMenuComponentListener> _listeners;

    private Image _cachedCurrentImage;
    private String _cachedProgressString;

    private AbstractSpinAnimator _animator;
    private boolean _animatorStarted = false;

    public IndexingTrayMenuSection(IndexingPoller indexingPoller)
    {
        _listeners = Lists.newArrayList();
        _indexingPoller = indexingPoller;
        _indexingPoller.addListener(this);

        _animator = new AbstractSpinAnimator(null) {
            @Override
            protected void setImage(Image img)
            {
                _cachedCurrentImage = img;
            }
        };
        UI.rnc().addListener(this);
    }

    @Override
    public void addListener(ITrayMenuComponentListener l)
    {
        _listeners.add(l);
    }

    @Override
    public void populateMenu(Menu menu)
    {
        _cachedMenu = menu;
        TrayMenuPopulator populator = new TrayMenuPopulator(menu);
        _indexingStats1 = populator.addMenuItem("Indexing your files...", null);
        _indexingStats1.setEnabled(false);
        updateInPlace();

        if (!_animatorStarted) {
            _animatorStarted = true;
            _animator.start();
        }
    }

    @Override
    public void updateInPlace()
    {
        // All these null checks are ugly, but necessary - we can't guarantee the GUI thread will
        // ask us to populate a menu before we get ritual notifications, and due to the need to
        // support Ubuntu's menu style, we can't throw this in here either.  Pity.
        if (_cachedMenu != null) {
            if (_indexingStats1 != null && _cachedCurrentImage != null) {
                _indexingStats1.setImage(_cachedCurrentImage);
            }
            if (_cachedProgressString != null) {
                if (_indexingStats2 == null) {
                    TrayMenuPopulator p = new TrayMenuPopulator(_cachedMenu);
                    _indexingStats2 = p.addMenuItemAfterItem(_cachedProgressString, _indexingStats1, null);
                    _indexingStats2.setEnabled(false);
                } else {
                    _indexingStats2.setText(_cachedProgressString);
                }
            }
        }
    }

    // Called on GUI thread.
    private void updateProgress(PBIndexingProgress progress)
    {
        Preconditions.checkState(GUI.get().isUIThread());
        _cachedProgressString = progress.getFiles() + " files in " +
                progress.getFolders() + " folders";
        updateInPlace();
        notifyListeners();
    }

    // N.B. this function must be called from the GUI thread.
    private void notifyListeners()
    {
        Preconditions.checkState(GUI.get().isUIThread());
        for (ITrayMenuComponentListener listener : _listeners) {
            listener.onTrayMenuComponentChange();
        }
    }

    @Override
    public void onNotificationReceived(final PBNotification pb)
    {
        if (pb.getType() == Type.INDEXING_PROGRESS) {
            GUI.get().asyncExec(new Runnable()
            {
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

        GUI.get().asyncExec(new Runnable()
        {
            @Override
            public void run()
            {
                if (_indexingStats1 != null && !_indexingStats1.isDisposed()) {
                    _indexingStats1.dispose();
                }
                if (_indexingStats2 != null && !_indexingStats2.isDisposed()) {
                    _indexingStats2.dispose();
                }
                _indexingStats1 = null;
                _indexingStats2 = null;
                // Trigger a menu refresh
                notifyListeners();
            }
        });
    }
}
