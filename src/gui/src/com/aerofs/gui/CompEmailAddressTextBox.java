package com.aerofs.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import com.aerofs.base.id.UserID;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.events.VerifyEvent;

import com.aerofs.lib.Util;
import com.aerofs.lib.notifier.ConcurrentlyModifiableListeners;

public class CompEmailAddressTextBox extends Composite
{
    public static interface IInputChangeListener
    {
        void inputChanged();
    }

    private final Text _text;

    private List<UserID> _userIDs = Collections.emptyList();

    private int _invalidAddresses;

    private final Pattern _pattern = Pattern.compile("[\\p{Space},;]+");

    private final ConcurrentlyModifiableListeners<IInputChangeListener> _ls =
            ConcurrentlyModifiableListeners.create();

    /**
     * Create the composite.
     */
    public CompEmailAddressTextBox(Composite parent, int style)
    {
        super(parent, style);
        setLayout(new FillLayout(SWT.HORIZONTAL));

        _text = new Text(this, SWT.BORDER | SWT.WRAP | SWT.MULTI);
        _text.addVerifyListener(new VerifyListener() {
            @Override
            public void verifyText(VerifyEvent ev)
            {
                verify(GUIUtil.getNewText(_text, ev));
            }
        });
    }

    private void verify(String text)
    {
        if (text == null) text = _text.getText();

        _userIDs = new ArrayList<UserID>();
        _invalidAddresses = 0;

        String[] tokens = _pattern.split(text);
        for (String token : tokens) {
            if (token.isEmpty()) {
            } else if (Util.isValidEmailAddress(token)) {
                _userIDs.add(UserID.fromExternal(token));
            } else {
                _invalidAddresses++;
            }
        }

        try {
            for (IInputChangeListener l : _ls.beginIterating_()) {
                l.inputChanged();
            }
        } finally {
            _ls.endIterating_();
        }
    }

    public void addInputChangeListener(IInputChangeListener l)
    {
        _ls.addListener_(l);
    }

    public List<UserID> getValidUserIDs()
    {
        return _userIDs;
    }

    public int getInvalidUserIDCount()
    {
        return _invalidAddresses;
    }

    @Override
    protected void checkSubclass()
    {
        // Disable the check that prevents subclassing of SWT components
    }

    @Override
    public void setEnabled(boolean b)
    {
        GUIUtil.setEnabled(_text, b);
        super.setEnabled(b);
    }
}
