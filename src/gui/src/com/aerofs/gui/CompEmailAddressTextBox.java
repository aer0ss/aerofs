package com.aerofs.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.events.VerifyEvent;

import com.aerofs.lib.Util;
import com.aerofs.lib.notifier.Listeners;

public class CompEmailAddressTextBox extends Composite
{
        public static interface IInputChangeListener
        {
                void inputChanged();
        }

        private final Text _text;

        private List<String> _addresses = Collections.emptyList();

        private int _invalidAddresses;

        private final Pattern _pattern = Pattern.compile("[\\p{Space},;]+");

        private final Listeners<IInputChangeListener> _ls = Listeners.newListeners();

        /**
         * Create the composite.
         * @param parent
         * @param style
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

                _addresses = new ArrayList<String>();
                _invalidAddresses = 0;

                String[] tokens = _pattern.split(text);
                for (String token : tokens) {
                        if (token.isEmpty()) continue;
                        else if (Util.isValidEmailAddress(token)) _addresses.add(token.toLowerCase());
                        else _invalidAddresses++;
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

        public List<String> getValidAddresses()
        {
                return _addresses;
        }

        public int getInvalidAddressesCount()
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
