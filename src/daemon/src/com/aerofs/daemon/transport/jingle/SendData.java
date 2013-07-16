package com.aerofs.daemon.transport.jingle;

import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.transport.xmpp.XMPPUtilities;

public class SendData
{
    private final byte[][] _bss;
    private final IResultWaiter _waiter;
    private int _cur = -1;
    private final byte[] _bsHeader;

    public SendData(byte[][] bss, IResultWaiter waiter)
    {
        _bss = bss;
        _waiter = waiter;

        int len = 0;
        for (byte[] bs : bss) len += bs.length;
        _bsHeader = XMPPUtilities.writeHeader(len);
    }

    /**
     * @return null if there's no more element
     */
    public byte[] current()
    {
        return _cur < 0 ? _bsHeader : (_cur == _bss.length ? null : _bss[_cur]);
    }

    public void next()
    {
        _cur++;
    }

    public IResultWaiter waiter()
    {
        return _waiter;
    }
}
