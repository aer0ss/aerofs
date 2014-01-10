/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.audit.client;

import java.io.IOException;

public interface IAuditorClient
{
    /**
     * Submit an auditable event synchronously.
     *
     * <strong>IMPORTANT:</strong> you can do multiple {@code submit} calls simultaneously
     */
    void submit(String content) throws IOException;
}
