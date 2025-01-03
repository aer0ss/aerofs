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

        _sb.append("\n")
                .append(header)
                .append("\n\n")
                .append(body);
    }

    @Override
    public void addSignature(String valediction, String name, String ps)
    throws IOException
    {
        if (_finalized) throw new IOException("cannot add signature to a finalized email");

        _sb.append("\n\n")
                .append(valediction)
                .append("\n")
                .append(name)
                .append("\n\n")
                .append(ps);
    }

    public String getEmail() {

        if (!_finalized) {
            _finalized = true;
            _sb.append(_unsubscribe);
        }
        return _sb.toString();
    }
}
