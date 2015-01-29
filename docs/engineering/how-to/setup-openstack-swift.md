Set up Swift-S3 for dev & testing with AeroFS
---

1. Follow http://www.piware.de/2014/03/creating-a-local-swift-server-on-ubuntu-for-testing/
to set up a local Swift server and client

2. Use the following command to create a bucket, upload a file to the
bucket, and verify:

        $ swift -A http://127.0.0.1:8080/auth/v1 -U testproj:testuser -K testpwd upload testbucket <an_existing_file_on_local_fs>
        $ swift ... list testbucket

3. Follow https://github.com/fujita/swift3 to install the Swift-S3 proxy, and:

        $ sudo swift-init all restart

4. Install s3curl and Write ~/.s3curl with the following content:

        %awsSecretAccessKeys = (
            testid => {
                id => 'testproj:testuser',
                key => 'testpwd',
            },
        );

5. Follow https://github.com/rtdp/s3curl to test the proxy:

        $ s3curl.pl --id testid http://127.0.0.1:8080/testbucket

5. Fix the ETag bug in Swift as mentioned in the support article.