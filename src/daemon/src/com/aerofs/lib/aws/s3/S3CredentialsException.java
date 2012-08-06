package com.aerofs.lib.aws.s3;


public class S3CredentialsException extends S3InitException
{
    private static final long serialVersionUID = 0L;

    public S3CredentialsException()
    {
    }

    public S3CredentialsException(Throwable cause)
    {
        super(cause);
    }
}