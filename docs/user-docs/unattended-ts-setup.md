# Unattended Team Server Setup

1. Run `touch ~/.aerofsts/unattended-setup.properties`
2. Populate the **unattended-setup.properties** file with the following contents:

    `userid = admin@acme.com` [Required]
    `password = admin_password` [Required]

    **Storage Type [Optional - default is LINKED storage]:**
    `storage_type = LINKED` [Preserves folder structure storage]
    `storage_type = LOCAL` [Uses compressed and deduplicated storage]
    `storage_type = S3` [Uses compressed and deduplicated storage pn S3]

    **S3 Credentials [Optional - required only if you specified S3 for the storage type]:**
    `s3_endpoint = <endpoint>`
    `s3_bucket_id = <bucket_name>`
    `s3_access_key = <access_key>`
    `s3_secret_key = <secret_key>`
    `s3_encryption_password = <encryption_password>`

3. Run `aerofsts-cli`

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
    s3_encryption_password = passencrypt
