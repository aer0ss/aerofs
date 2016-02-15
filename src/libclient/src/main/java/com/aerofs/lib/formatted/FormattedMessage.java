package com.aerofs.lib.formatted;

/**
 * Container for returning both obfuscated and plain text formatted messages.
 */
public class FormattedMessage {
    public final String _internal;
    public final String _plainText;

    public FormattedMessage(String plainText)
    {
        _internal = plainText;
        _plainText = plainText;
    }

    public FormattedMessage(String plainText, String internal)
    {
        _internal = internal;
        _plainText = plainText;
    }
}
