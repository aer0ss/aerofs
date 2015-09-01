## Client -> Service
  - Expected header:
    Authorization: Aero-Device-Cert <base64(utf8(username))> <hex-encoded-did>
  - Service must also verify the presence of the Verify: SUCCESS header
  - Service must also verify the presence and correctness of the cname in the DName header
    - cname should be alphabetencode(sha256(utf8(username)+did)), see BaseSecUtil.java, AeroDeviceCert.java or secutil.py or charles.py
  - If possible, Service should verify that the certificate's serial number is not revoked.
    - Future work: RPC with service-to-service auth to ask Sparta if a certificate is still valid?


## ServiceA -> ServiceB
  - A "deployment secret" shall be present on the filesystem at `/data/deployment_secret`.  It shall consist of 32 hexadecimal characters, followed by a newline.

  - For internal service function:

    Authorization: Aero-Service-Shared-Secret <service> <secret>
    - <service> must identify ServiceA with an alphanumeric string
    - <secret> is the 128-bit deployment secret expressed as 32 hexadecimal characters
    - Service must verify that <secret> matches the deployment secret's value


  - To perform an action on behalf of a user
    - User provenance
      Authorization: Aero-Delegated-User <service> <secret> <base64(utf8(userid))>
      - ServiceA must have verified that the user is authentic before issuing this request
      - ServiceB must verify that <secret> matches the deployment secret's value

    - Device provenance
      Authorization: Aero-Delegated-User-Device <service> <secret> <base64(utf8(userid))> <hex-encoded-did>
      - ServiceA must have verified that the user and did are authentic before issuing this request
      - ServiceB must verify that <secret> matches the deployment secret's value

