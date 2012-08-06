/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.notifier;

public interface IListenerVisitor<ListenerType>
{
    void visit(ListenerType listener);
}
