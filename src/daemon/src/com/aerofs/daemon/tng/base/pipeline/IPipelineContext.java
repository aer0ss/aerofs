/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.pipeline;

import com.aerofs.lib.id.DID;

public interface IPipelineContext
{
    void sendIncomingEvent_(IPipelineEvent<?> event);

    void sendOutgoingEvent_(IPipelineEvent<?> event);

    IConnection getConnection_();

    DID getDID_();

    IStateContainer getStateContainer_();
}