package com.aerofs.sp.server.email;

import com.aerofs.sp.common.SubscriptionParams;

import java.io.IOException;

public class TextEmail implements IEmail {

    private final StringBuilder _sb = new StringBuilder();

    private final String _unsubscribe;

    private boolean _finalized = false;

    TextEmail(boolean unsubscribe, String unsubscribeId)
    {
        _unsubscribe = unsubscribe ? "\n\n\nIf you prefer not to receive these reminders " +
                "please go to " + SubscriptionParams.UNSUBSCRIPTION_URL + unsubscribeId : "";
    }
    @Override
    public void addSection(final String header, final String body)
    throws IOException
    {
        if (_finalized) throw new IOException("cannot add section to a finalized email");
        int len = header.length();

        _sb.append("\n");

        _sb.append(header);

        _sb.append("\n");
        for (int i =0; i < len; i++) _sb.append("=");
        _sb.append("\n");

        _sb.append("\n");
        _sb.append(body);

    }

    @Override
    public void addSignature(String valediction, String name, String ps)
    throws IOException
    {
        if (_finalized) throw new IOException("cannot add signature to a finalized email");

        _sb.append("\n\n");
        _sb.append(valediction);
        _sb.append("\n" + name);

        _sb.append("\n\n" + ps);

    }

    public String getEmail() {

        if (!_finalized) {
            _finalized = true;
            _sb.append(_unsubscribe);
        }
        return _sb.toString();
    }

}
