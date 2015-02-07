# Set up Swift-S3 for Integration with AeroFS

## Introduction

The AeroFS
[Team Server](https://support.aerofs.com/hc/en-us/articles/201439424-Team-Server),
which serves as a central storage node within AeroFS systems, natively supports
the S3 protocol. While OpenStack Swift integration is planned and scheduled on
our roadmap, it is currently not supported natively.

To configure your Team Server to work with Swift, execute the following
step-by-step procedure to install the swift3 plugin for your Swift instance.
This will allow your AeroFS Team Server to communicate with your OpenStack swift
infrastructure via a S3-proxy.

## Step-by-step Procedure

1. Follow
   [these instructions](http://www.piware.de/2014/03/creating-a-local-swift-server-on-ubuntu-for-testing/)
   to set up a local Swift server and client, or use your existing OpenStack
   setup.

2. If using the above developer setup, use the following command to create a
   bucket, upload a file to the bucket, and verify:

        alias swift-with-auth='swift -A http://127.0.0.1:8080/auth/v1 -U <proj>:<user> -K <pass>'
        swift-with-auth post   <bucket>
        swift-with-auth upload <bucket> <file>
        swfit-with-auth list   <bucket>

   Or, create a bucket using the normal bucket creation workflow of your
   OpenStack system in project `<proj>`, with user `<user>`, password `<pass>`
   and bucket name `<bucket>`.

3. Follow [these instructions](https://github.com/fujita/swift3) to install the
   Swift-S3 proxy, and:


   You must also patch swift3 to work around a protocol compatibility issue
   related to the ETag field as follows.

        apt-get install python-dev
        pip install --upgrade eventlet
        pip install --upgrade eventlet

   Patch wsgi.py in eventlet: (the file is at
   `/usr/local/lib/python2.*/dist-packages/eventlet/wsgi.py` for the default
   install) Find "Response header capitalization" in "start_response"
   function, and insert:

        response_headers = [('ETag', v) if k == 'Etag' else (k, v) for k, v in response_headers]

   right after the "response_headers = [...]" line.

   Finally, restart all swift services:

        sudo swift-init all restart

4. Install `s3curl` and write `~/.s3curl` with the following content:

        %awsSecretAccessKeys = (
            <id> => {
                id => '<proj>:<user>',
                key => '<pass>',
            },
        );

5. Follow [these instructions](https://github.com/rtdp/s3curl) to test the
   proxy. Be sure to configure your localhost endpoint in `s3curl.pl`
   as follows:

        my @endpoints = ( '127.0.0.1');

   Once configured, test your endpoint with:

        s3curl.pl --id <id> http://127.0.0.1:8080/<bucket>

6. At this point you are ready to set up your AeroFS Team Server. Download
   the Team Server application from your AeroFS Appliance interface and
   execute the `aerofsts-cli` application to begin configuration. You may
   optionally use
   [these upstart scripts](https://github.com/mpillar/aerofs-upstart) for your
   Team Server.

   Example Team Server configuration follows.

        Endpoint: `http://127.0.0.1:8080`
        Key: `<proj>:<user>`
        Secret: `<pass>`

   FIXME: the TS currently does not play nice with Swift due to a change in
   the S3 authorization protocol. We updated our client library, but swift3
   does not support the new scheme.

