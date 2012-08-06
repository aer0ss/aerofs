package com.aerofs.daemon.transport.xmpp.jingle;

/**
 * a class should implement the interface if it contains any libjingle proxy
 * object. the delete() method must call delete() on all the containing proxy
 * objects. the caller should reset the reference to this object to null,
 * if possible.
 *
 * it's a good practice to set pointers to null after delete() the
 * proxy object, because a crash in Java is better than a crash in C.
 *
 */
public interface IProxyObjectContainer {

    void delete_();

}
