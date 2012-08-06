package com.aerofs.lib.aws.s3;

import java.io.IOException;

public class S3InitException extends IOException
{
    private static final long serialVersionUID = 0L;

    public S3InitException()
    {
    }

    public S3InitException(Throwable cause)
    {
        super(cause);
    }
}
