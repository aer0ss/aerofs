# Unattended Team Server Setup

## Procedure

1. Run `touch ~/.aerofsts/unattended-setup.properties`
2. Populate the **unattended-setup.properties** file with the following contents:

    `userid = admin@acme.com` [Required]  
    `password = admin_password` [Required]

    **Storage Type [Optional - default is LINKED storage]:**

    `storage_type = LINKED` [Preserves folder structure storage]  
    `storage_type = LOCAL` [Uses compressed and deduplicated storage]  
    `storage_type = S3` [Uses compressed and deduplicated storage pn S3]
    `storage_type = SWIFT` [Uses compressed and deduplicated storage on OpenStack Swift]

    **S3 Credentials [Optional - required only if you specified S3 for the storage type]:**

    `s3_endpoint = <endpoint>`
    `s3_bucket_id = <bucket_name>`
    `s3_access_key = <access_key>`
    `s3_secret_key = <secret_key>`
    `remote_storage_encryption_password = <encryption_password>`

    **Swift Credentials [Optional - required only if you specified SWIFT for the storage type]:**

    `swift_auth_mode = <basic (ony method supported>`
    `swift_username = <username>`
    `swift_password = <password>`
    `swift_url = <endpoint_url>`
    `swift_container = <existing_container>`
    `remote_storage_encryption_password = <encryption_password>`

3. Run `aerofsts-cli`

## Examples

Sample unattended-setup.properties file for LINKED Storage:

    userid = john@acme.com
    password = testpass

Sample unattended-setup.properties file for LOCAL Storage:

    userid = john@acme.com
    password = testpass
    storage_type = LOCAL

Sample unattended-setup.properties file for S3 Storage:

    userid = john@acme.com
    password = testpass
    storage_type = S3
    s3_endpoint = https://s3.amazonaws.com
    s3_bucket_id = testbucket
    s3_access_key = JKHIEAKGHGGE5K89
    s3_secret_key = Lkihg/ieabherGuDier2x9pULle
    remote_storage_encryption_password = passencrypt


Sample unattended-setup.properties file for SWIFT Storage:

    userid = john@acme.com
    password = testpass
    storage_type: SWIFT
    swift_auth_mode: basic
    swift_username: test:tester
    swift_password: testing
    swift_url: http://192.168.33.10:8080/auth/v1.0
    swift_container: container_aerofs
    remote_storage_encryption_password: aaa
