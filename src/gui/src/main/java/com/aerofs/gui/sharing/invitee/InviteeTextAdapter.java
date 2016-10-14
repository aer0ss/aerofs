/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.sharing.invitee;

import com.aerofs.base.Loggers;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIExecutor;
import com.aerofs.gui.Images;
import com.aerofs.gui.sharing.SharingModel;
import com.aerofs.gui.sharing.Subject;
import com.aerofs.gui.sharing.Subject.InvalidSubject;
import com.aerofs.lib.os.OSUtil;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.swtdesigner.SWTResourceManager;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ST;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.GlyphMetrics;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Widget;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.Futures.immediateCancelledFuture;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.apache.commons.lang.StringUtils.isNotEmpty;

/**
 * InviteeTextAdapter hides all the nasty detail involved to support auto-complete and exposes a
 * nice interface for other components to work with.
 *
 * Couple key points:
 * - We roll a custom suggestion popup. Most of its implementation is based on JFace's
 *   ContentProposalAdapter. Our implementation supports less features and has a different set of
 *   annoying bugs.
 *
 *   Known issues with JFace's impl:
 *   - JFace's suggestions are affected by the position of the caret in the text. What this means is
 *     that suggestions are reset and repopulated as the user moves the caret left and right.
 *     Suppose the user has the third suggestion highlighted, then moving the caret to the left
 *     would've reset the highlighted suggestion to the first.
 *   - JFace's impl. requires the suggestions to be computed synchronously and on the UI thread.
 *     There's no way to notify JFace that suggestions have changed nor refreshing the GUI; this
 *     doesn't work well with our asynchronous model.
 *   Known issues with our impl:
 *   - Unlike JFace's suggestions, the suggestion UI doesn't wrap around. In other words, if the
 *     last suggestion is highlighted and the user press ARROW_DOWN, then the last suggestion is
 *     still highlighted instead of the first (JFace behaviour). I personally think this makes more
 *     sense.
 *   - Due to differences between the platforms, I've opted to allocate enough space so we'll never
 *     see scrollbars on the suggestions popup. Unfortunately, this also means that the popup will
 *     sometimes appear taller than it needs to be.
 *   - Due to impl. detail, we are unable to determine how wide the suggestions window needs to be.
 *     Experiments show that packing each time doesn't work due to scrollbars, so we allocate a
 *     fixed width and pray that it's enough.
 * - Invitees are handled using invitee bubbles in the same manner as how other apps support
 *   emoticons: replace the string pattern with U+FFFC object replacement character, indicates how
 *   much space is required to StyledText, and then do custom painting on the space allocated by
 *   StyledText.
 * - In order to smoothly handle events from both the text and the popup, it's simpler to forward
 *   events back and forth. JFace also took this approach.
 * - StyledText is not native and is built on top of SWT, so it has an unusual sequence of events.
 *   When the user press a key, the following event occurs in sequence:
 *   * Traverse (if related)
 *   * VerifyKey
 *   * Verify (if text modification is needed)
 *   * Modify (if text modification is applied)
 *   * KeyDown (one would imagine this to occur before Verify and Modify; this would be true if
 *     StyledText is a native SWT widget but StyledText is not native)
 *   * KeyUp
 * - The text body of StyledText is split into two sections: the prefix of the text body is a list
 *   of place holders, each holding an invitee. This list contains invitees the user has entered so
 *   far. The remainder of the text body is called the pending text, and the user modifies the
 *   pending text to input the next invitee.
 */
public class InviteeTextAdapter
{
    private static final Logger l = Loggers.getLogger(InviteeTextAdapter.class);
    public static final String PLACE_HOLDER = "\uFFFC";

    // the spacing between the drawing region and the bubble
    private static final int V_MARGIN = 2;
    private static final int H_MARGIN = 2;

    // the spacing between the bubble's border and the icon
    private static final int V_OFFSET = 2;
    private static final int H_OFFSET = 6;

    // the spacing between the icon and the text
    private static final int H_SPACING = 2;

    private final Color COLOR_FOREGROUND    = SWTResourceManager.getColor(SWT.COLOR_BLACK);
    private final Color COLOR_BACKGROUND    = SWTResourceManager.getColor(0x71, 0xC9, 0xF1);

    // N.B. these cannot be static because they depends on ICON_BASE having being initialized
    private final Image ICON_BASE           = Images.get(Images.ICON_USER);
    private final int ICON_WIDTH            = ICON_BASE.getImageData().width;
    private final int ICON_HEIGHT           = ICON_BASE.getImageData().height;

    private final SharingModel          _model;
    private final StyledText            _text;
    private final Shell                 _shell;
    private final Composite             _cmpContent;
    private final TableViewer           _viewer;
    private final Table                 _table;
    private final TableViewerColumn     _column;
    private final InviteeLabelProvider  _labelProvider;

    private List<Subject>                   _suggestions    = emptyList();
    private ListenableFuture<List<Subject>> _lastQueryTask  = immediateCancelledFuture();

    public InviteeTextAdapter(SharingModel model, StyledText text)
    {
        _model          = model;
        _text           = text;
        _shell          = new Shell(_text.getShell(), SWT.TOOL | SWT.ON_TOP);
        _cmpContent     = new Composite(_shell, SWT.NONE);
        _viewer         = new TableViewer(_cmpContent, SWT.SINGLE | SWT.READ_ONLY);
        _table          = _viewer.getTable();
        _column         = new TableViewerColumn(_viewer, SWT.NONE);
        _labelProvider  = new InviteeLabelProvider();

        initializeSuggestionsPopup();
    }

    private void initializeSuggestionsPopup()
    {
        _viewer.setContentProvider(ArrayContentProvider.getInstance());
        _viewer.setLabelProvider(_labelProvider);

        if (!OSUtil.isLinux()) {
            // turns out this looks great on Windows and OS X but looks horrible on Ubuntu with
            // Unity, which is the default desktop environment :(
            // Long story short, we can't trust the taste of Linux desktop environment developers.
            _table.setForeground(SWTResourceManager.getColor(SWT.COLOR_INFO_FOREGROUND));
            _table.setBackground(SWTResourceManager.getColor(SWT.COLOR_INFO_BACKGROUND));
            _table.setBackgroundMode(SWT.INHERIT_FORCE);
        }

        RowLayout layout = new RowLayout(SWT.VERTICAL);
        layout.marginWidth  = 0;
        layout.marginHeight = 0;
        layout.marginTop    = 0;
        layout.marginBottom = 0;
        layout.marginLeft   = 0;
        layout.marginRight  = 0;
        layout.pack         = true;
        _shell.setLayout(layout);
        _cmpContent.setLayoutData(new RowData(300, 6 * _table.getItemHeight()));

        TableColumnLayout contentLayout = new TableColumnLayout();
        contentLayout.setColumnData(_column.getColumn(), new ColumnWeightData(1));
        _cmpContent.setLayout(contentLayout);

        _shell.pack();
    }

    // N.B. _Here be dragons_. Please test thoroughly on all platforms.
    public void installListeners()
    {
        // uncomment next line to install info listeners for debugging purposes
        // installInfoListeners();

        // Query the model for suggestions when the text is modified
        _text.addModifyListener(event -> {
            if (!_lastQueryTask.isDone()) {
                _lastQueryTask.cancel(true);
            }

            _lastQueryTask = _model.getSuggestions(getPendingText());

            addCallback(_lastQueryTask, new FutureCallback<List<Subject>>() {
                @Override
                public void onSuccess(List<Subject> suggestions)
                {
                    setSuggestions(suggestions);
                }

                @Override
                public void onFailure(Throwable t)
                {
                    if (t instanceof CancellationException) {
                        // expected
                    } else {
                        l.warn("Failed to query for suggestions: {}", t.getMessage());
                    }
                }
            }, new GUIExecutor(_text));
        });

        // forward DefaultSelection to the table first
        _text.addListener(SWT.DefaultSelection, e -> {
            if (e.doit && _table.isVisible()) _table.notifyListeners(e.type, e);
        });

        // only handle DefaultSelection if the table doesn't handle it
        _text.addListener(SWT.DefaultSelection, e -> {
            if (!e.doit || isBlank(getPendingText())) return;
            addInvitee(_model._factory.fromUserInput(getPendingText()));
            e.doit = false;
        });

        // forward Traverse to the popup. This is used primarily for handling TRAVERSE_ESCAPE.
        _text.addListener(SWT.Traverse, e -> {
            if (e.doit && _shell.isVisible()) _shell.notifyListeners(e.type, e);
        });

        // do not allow modifications to take place outside of pending text region
        _text.addListener(SWT.Verify, e -> {
            if (e.doit && isNotEmpty(e.text) && e.start < getPendingTextIndex()) {
                e.doit = false;
            }
        });

        // forward key events to the table first
        _text.addListener(ST.VerifyKey, e -> {
            if (e.doit && _table.isVisible()) _table.notifyListeners(SWT.KeyDown, e);
            // it is necessary to reset the type of the event back to VerifyKey so other VerifyKey
            // listeners can continue to handle this event when the event is not handled by the
            // table.
            e.type = ST.VerifyKey;
        });

        // only handle key events when the table doesn't handle them
        _text.addListener(ST.VerifyKey, e -> {
            if (!e.doit) return;
            switch (e.keyCode) {
            case SWT.LF:
            case SWT.CR:
            case SWT.TAB:
            case ',':
            case ';':
                _text.notifyListeners(SWT.DefaultSelection, e);
                e.doit = false;
            }
        });

        // custom painting code to draw the invitee bubble
        _text.addPaintObjectListener(e -> {
            if (!(e.style.data instanceof Subject)) return;
            Subject data = (Subject)e.style.data;

            GC gc = e.gc;
            GlyphMetrics metrics = e.style.metrics;

            gc.setForeground(COLOR_FOREGROUND);
            gc.setBackground(COLOR_BACKGROUND);

            int bubbleWidth = metrics.width - 2 * H_MARGIN;
            int bubbleHeight = metrics.ascent + metrics.descent - 2 * V_MARGIN;

            // it is necessary to fill in the bubble that is 1 pixel wider and taller. Otherwise
            // we'll miss some pixels.
            gc.fillRoundRectangle(e.x + H_MARGIN, e.y + V_MARGIN, bubbleWidth + 1, bubbleHeight + 1,
                    H_OFFSET, bubbleHeight / 2);
            gc.drawRoundRectangle(e.x + H_MARGIN, e.y + V_MARGIN, bubbleWidth, bubbleHeight,
                    H_OFFSET, bubbleHeight / 2);

            Image icon = _labelProvider.getImage(data);

            // _here be dragons_: test thoroughly on all platforms if you change the math here
            gc.drawImage(icon, e.x + H_MARGIN + H_OFFSET, e.y +
                    (metrics.ascent + metrics.descent - ICON_HEIGHT) / 2);
            gc.drawString(data.getLabel(), e.x + H_MARGIN + H_OFFSET + ICON_WIDTH + H_SPACING,
                    e.y + V_MARGIN + V_OFFSET, true);
        });

        // handle the case when the user selects a suggestion
        _table.addListener(SWT.DefaultSelection, e -> {
            if (!e.doit || !_table.isVisible() || _table.getSelectionIndex() < 0) return;
            addInvitee(_suggestions.get(_table.getSelectionIndex()));
            e.doit = false;
        });

        // handle suggestion navigation
        _table.addListener(SWT.KeyDown, e -> {
            if (!e.doit || !_table.isVisible()) return;
            switch (e.keyCode) {
            case SWT.ARROW_UP:
                setSelectedSuggestion(_table.getSelectionIndex() - 1);
                break;
            case SWT.ARROW_DOWN:
                setSelectedSuggestion(_table.getSelectionIndex() + 1);
                break;
            case SWT.PAGE_UP:
                setSelectedSuggestion(0);
                break;
            case SWT.PAGE_DOWN:
                setSelectedSuggestion(6);
                break;
            default:
                return;
            }
            e.doit = false;
        });

        // merely hide (and not dispose) the popup on escape
        _shell.addListener(SWT.Traverse, e -> {
            if (e.detail == SWT.TRAVERSE_ESCAPE) {
                _shell.setVisible(false);
                e.doit = false;
            }
        });

        // used to detect when the popup should be hidden because the important widgets have lost
        // focus. Since the focus is not yet lost when the listeners are called, we have to schedule
        // a future to hide the popup to handle the case where the focus simply transfers between
        // important widgets.
        Listener lostFocusListener = e -> GUI.get().asyncExec(() -> {
            if (_shell.isDisposed()) return;
            if (Display.getCurrent().getActiveShell() == _shell) return;
            if (!_text.isDisposed() && _text.isFocusControl()) return;
            _shell.setVisible(false);
            e.doit = false;
        });
        _table.addListener(SWT.FocusOut, lostFocusListener);
        _shell.addListener(SWT.Deactivate, lostFocusListener);
        _text.addListener(SWT.FocusOut, lostFocusListener);
    }

    public List<Subject> getValues()
    {
        List<Subject> values = Stream.of(_text.getStyleRanges())
                .map(styleRange -> (Subject)styleRange.data)
                .collect(toList());

        if (isNotBlank(getPendingText())) {
            // FIXME (AT): this will cause AeroFS to create an invitee on every text modified event.
            //   This is expensive.
            values.add(_model._factory.fromUserInput(getPendingText()));
        }

        return values;
    }

    public boolean isInputValid()
    {
        List<Subject> values = getValues();
        return values.size() > 0
                && !values.stream()
                .filter(subject -> subject instanceof InvalidSubject)
                .findAny()
                .isPresent();
    }

    private void addInvitee(Subject subject)
    {
        checkState(GUI.get().isUIThread());
        int offset = getPendingTextIndex();
        _text.replaceTextRange(offset, _text.getText().length() - offset, PLACE_HOLDER);

        StyleRange styleRange = new StyleRange();
        styleRange.start = offset;
        styleRange.length = 1;
        styleRange.data = subject;

        GC gc = new GC(_text);
        FontMetrics metrics = gc.getFontMetrics();
        styleRange.metrics = new GlyphMetrics(
                metrics.getAscent() + V_MARGIN + V_OFFSET,
                metrics.getDescent() + V_MARGIN + V_OFFSET,
                ICON_WIDTH + H_SPACING + gc.textExtent(subject.getLabel()).x
                        + 2 * (H_MARGIN + H_OFFSET));
        // it is necessary to dispose the GC otherwise the user will see weird behaviours on Ubuntu
        gc.dispose();

        _text.setStyleRange(styleRange);
        _text.setSelection(offset + 1);
        _text.showSelection();

        _text.notifyListeners(SWT.Modify, new Event());
    }

    private int getPendingTextIndex()
    {
        return _text.getText().lastIndexOf(PLACE_HOLDER) + 1;
    }

    private String getPendingText()
    {
        return _text.getText().substring(getPendingTextIndex()).trim();
    }

    private void setSuggestions(List<Subject> suggestions)
    {
        checkState(GUI.get().isUIThread());

        _suggestions = suggestions;
        _viewer.setInput(_suggestions);

        if (_suggestions.size() > 0) {
            _table.setSelection(0);
            Rectangle caret = _text.getCaret().getBounds();

            if (!_shell.isVisible()) {
                _shell.setLocation(_text.toDisplay(caret.x + 2, caret.y + caret.height + 2));
            }

            _shell.setVisible(true);
        } else {
            _shell.setVisible(false);
        }
    }

    private void setSelectedSuggestion(int index)
    {
        checkState(GUI.get().isUIThread());
        _table.setSelection(min(max(index, 0), _table.getItemCount() - 1));
        _table.redraw();
    }

    // used for debugging purposes, see installListeners()
    private void installInfoListeners()
    {
        Map<Integer, String> map = newHashMap();
        map.put(SWT.Traverse, "Traverse");
        map.put(ST.VerifyKey, "VerifyKey");
        map.put(SWT.Verify, "Verify");
        map.put(SWT.Modify, "Modify");
        map.put(SWT.KeyDown, "KeyDown");
        map.put(SWT.KeyUp, "KeyUp");
        map.put(SWT.Selection, "Selection");
        map.put(SWT.DefaultSelection, "DefaultSelection");
        map.put(SWT.Dispose, "Dispose");

        Listener listener = e -> l.info("{}: {} {}",
                map.get(e.type), e.doit ? "DoIt" : "Handled", e);

        for (int type : map.keySet()) {
            for (Widget w : newArrayList(_text, _shell, _table)) {
                w.addListener(type, listener);
            }
        }
    }

    private class InviteeLabelProvider extends LabelProvider
    {
        @Override
        public String getText(Object element)
        {
            if (element instanceof Subject) {
                return ((Subject)element).getDescription();
            } else {
                return "";
            }
        }

        @Override
        public Image getImage(Object element)
        {
            if (element instanceof Subject) {
                return ((Subject)element).getImage();
            } else {
                return null;
            }
        }
    }
}
