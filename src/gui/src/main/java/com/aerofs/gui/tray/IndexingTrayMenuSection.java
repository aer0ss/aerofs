/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.tray;

import com.aerofs.gui.AbstractSpinAnimator;
import com.aerofs.gui.GUI;
import com.aerofs.gui.tray.IndexingPoller.IIndexingCompletionListener;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.ritual_notification.IRitualNotificationListener;
import com.aerofs.ui.UIGlobals;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.UbuntuTrayItem;

public class IndexingTrayMenuSection extends DynamicTrayMenuComponent implements IRitualNotificationListener, IIndexingCompletionListener
{
    private MenuItem _indexingStats1;
    private MenuItem _indexingStats2;
    private Menu _cachedMenu;

    private final IndexingPoller _indexingPoller;

    private Image _cachedCurrentImage;
    private String _cachedProgressString;

    private AbstractSpinAnimator _animator;
    private boolean _animatorStarted = false;

    public IndexingTrayMenuSection(IndexingPoller indexingPoller)
    {
        _indexingPoller = indexingPoller;
        _indexingPoller.addListener(this);

        // no spinner for libappindicator-based menu: refresh rate insufficient
        if (!UbuntuTrayItem.supported()) {
            _animator = new AbstractSpinAnimator(null) {
                @Override
                protected void setImage(Image img)
                {
                    _cachedCurrentImage = img;
                    updateInPlace();
                }
            };
        }
        UIGlobals.rnc().addListener(this);
    }

    @Override
    public void populateMenu(Menu menu)
    {
        _cachedMenu = menu;
        TrayMenuPopulator populator = new TrayMenuPopulator(menu);
        _indexingStats1 = populator.addMenuItem("Indexing your files...", null);
        _indexingStats1.setEnabled(false);
        updateInPlace();

        if (_animator != null && !_animatorStarted) {
            _animatorStarted = true;
            _animator.start();
        }
    }

    @Override
    public void updateInPlace()
    {
        // All these null checks are ugly, but necessary - we can't guarantee the GUI thread will
        // ask us to populate a menu before we get ritual notifications.  We also have no guarantee
        // that this will not be called after onIndexingDone() is called.  Pity.
        if (_cachedMenu != null) {
            if (_indexingStats1 != null && _cachedCurrentImage != null &&
                    !_indexingStats1.isDisposed()) {
                _indexingStats1.setImage(_cachedCurrentImage);
            }

            if (_cachedProgressString != null) {
                if (_indexingStats2 == null || _indexingStats2.isDisposed()) {
                    // addMenuItemAfterItem only works on non-null, non-disposed items
                    if (_indexingStats1 != null && !_indexingStats1.isDisposed()) {
                        TrayMenuPopulator p = new TrayMenuPopulator(_cachedMenu);
                        _indexingStats2 = p.addMenuItemAfterItem(_cachedProgressString, _indexingStats1, null);
                        _indexingStats2.setEnabled(false);
                    }
                } else {
                    _indexingStats2.setText(_cachedProgressString);
                }
            }
        }
    }

    @Override
    public void onNotificationReceived(final PBNotification pb)
    {
        if (pb.getType() == Type.INDEXING_PROGRESS) {
            _cachedProgressString =
                    pb.getIndexingProgress().getFiles() + " files in " +
                    pb.getIndexingProgress().getFolders() + " folders";

            scheduleUpdate();
        }
    }

    @Override
    public void onNotificationChannelBroken()
    {
        // noop
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
                refresh();
            }
        });
    }
}
