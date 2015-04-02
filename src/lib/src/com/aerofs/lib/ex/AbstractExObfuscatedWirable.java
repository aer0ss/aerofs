/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.base.ex.IExObfuscated;
import com.aerofs.lib.formatted.FormattedMessage;
import com.aerofs.proto.Common.PBException;

abstract class AbstractExObfuscatedWirable extends AbstractExWirable implements IExObfuscated
{
    private static final long serialVersionUID = 1L;

    protected final String _plainTextMessage;

    protected AbstractExObfuscatedWirable()
    {
        super();
        _plainTextMessage = "";
    }

    protected AbstractExObfuscatedWirable(FormattedMessage message)
    {
        super(message._internal);
        _plainTextMessage = message._plainText;
    }

    protected AbstractExObfuscatedWirable(PBException pb)
    {
        super(pb);
        assert(pb.hasPlainTextMessageDeprecated());
        _plainTextMessage = pb.getPlainTextMessageDeprecated();
    }

    @Override
    public String getPlainTextMessage()
    {
        return _plainTextMessage;
    }
}
