/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core;

/**
 * The implemenetations register arbitrary event handlers to the CoreEventDispatcher
 */
public interface ICoreEventHandlerRegistrar
{
    /**
     * @param disp we use CoreEventDispatcher rather than EventDispatcher to avoid accidentally
     * registering events to a non-core dispatcher.
     */
    void registerHandlers_(CoreEventDispatcher disp);
}
