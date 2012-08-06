/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.event.net;

import com.aerofs.lib.id.DID;

import static com.aerofs.daemon.transport.lib.PulseManager.PulseToken;

/**
 * Event to be scheduled by the transport for rescheduled pulses
 */
public class EOTpSubsequentPulse implements IPulseEvent
{
    public EOTpSubsequentPulse(DID did, PulseToken tok, long inittimeout, long maxtimeout)
    {
        assert did != null && tok != null : ("invalid params");

        this.did = did;
        _tok = tok;
        _curtimeout = inittimeout;
        _maxtimeout = maxtimeout;
    }

    @Override
    public DID did()
    {
        return did;
    }

    @Override
    public PulseToken tok_()
    {
        return _tok;
    }

    public long curtimeout_()
    {
        return _curtimeout;
    }

    public int tries_()
    {
        return _tries;
    }

    public boolean killed_()
    {
        return _killed;
    }

    @Override
    public void tok_(PulseToken tok)
    {
        assert tok.equals(_tok) :
            ("mismatched pulse sequence ids ex:" + _tok + " ac:" + tok);
        _tok = tok;
    }

    public void addtry_()
    {
        _tries++;

        if (_curtimeout == _maxtimeout) return;
        if ((_curtimeout *= 2) > _maxtimeout) _curtimeout = _maxtimeout;
    }

    public void markkilled_()
    {
        _killed = true;
    }

    @Override
    public String toString()
    {
        return "tpsubspulse: d:" + did + " tok:" + _tok + " tri:" + _tries +
            " kld:" + _killed + " cto:" + _curtimeout + " mto:" + _maxtimeout;
    }

    //
    // members
    //

    private final DID did;
    private PulseToken _tok;
    private boolean _killed = false;
    private int _tries = 1;
    private long _curtimeout;
    private long _maxtimeout;
}
