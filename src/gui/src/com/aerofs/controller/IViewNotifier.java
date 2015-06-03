package com.aerofs.controller;

import javax.annotation.Nullable;

public interface IViewNotifier
{
    enum Type
    {
        UPDATE,
    }

    /**
     * This method is called by the controller to deliver a notification to the view.
     *
     * This method _must_ be thread-safe.
     */
    void notify(Type type, @Nullable Object notification);
}
