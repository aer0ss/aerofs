package com.aerofs.daemon.rest.util;

import javax.ws.rs.core.Response.Status.Family;
import javax.ws.rs.core.Response.StatusType;

/**
 * Fill in gaps in Jersey...
 */
public class HttpStatus
{
    public static final StatusType PARTIAL_CONTENT = new StatusType()
    {
        @Override
        public int getStatusCode()
        {
            return 206;
        }

        @Override
        public Family getFamily()
        {
            return Family.SUCCESSFUL;
        }

        @Override
        public String getReasonPhrase()
        {
            return "Partial Content";
        }
    };

    public static final StatusType UNSATISFIABLE_RANGE = new StatusType() {
        @Override
        public int getStatusCode()
        {
            return 416;
        }

        @Override
        public Family getFamily()
        {
            return Family.CLIENT_ERROR;
        }

        @Override
        public String getReasonPhrase()
        {
            return "Requested Range Not Satisfiable";
        }
    };
}
