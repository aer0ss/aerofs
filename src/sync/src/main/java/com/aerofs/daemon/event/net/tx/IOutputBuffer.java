package com.aerofs.daemon.event.net.tx;

//all the methods of this class and of classes returned by this class
//must NOT block
//
public interface IOutputBuffer
{
    // TODO: returns an array of bytes for scatter/gather
    byte[] byteArray();
}
