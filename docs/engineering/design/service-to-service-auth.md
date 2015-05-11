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


## TODO:
1. Add deployment_secret to client libraries (almost done)
    - Auditor client
      - Java (done, https://gerrit.arrowfs.org/4041 )
      - Python (url_sharing_view.py) (done, https://gerrit.arrowfs.org/3721 )
    - VK rest clients
      - Java (done, https://gerrit.arrowfs.org/3937 )
      - Python (charles.py) (done, https://gerrit.arrowfs.org/3789 )
      - Python (devices_view.py) (done, https://gerrit.arrowfs.org/3820 )
    - config clients
      - only needed for server configs and write access
        - python client done ( https://gerrit.arrowfs.org/3791 )
        - java client done ( https://gerrit.arrowfs.org/3833 )
        - docker scripts done ( https://gerrit.arrowfs.org/4000 )
    - bootstrap tasks
      - bifrost interactions done ( https://gerrit.arrowfs.org/3821 )
      - config - done ( https://gerrit.arrowfs.org/3833 )
    - bifrost clients
      - Java (bootstrap part is done)
        - are there others? SP appears to only use public, unauthed routes, so I think we're done?
      - Python (web/oauth.py) done ( https://gerrit.arrowfs.org/3800 )
    - curl-using things
      - crt-create done ( https://gerrit.arrowfs.org/3701 )
      - sanity checks/pagerduty scripts
2. servers: verify that we can accept requests with these authorization schemes
    - Additional resource-specific access checks may be required to support externalizing routes
      - For instance, an IAuthToken or whatever produced from an Aero-Service-Shared-Secret scheme header probably doesn't need to check that bunker is allowed to interrogate things about drew@aerofs.com, but Aero-Delegated-User probably should check that yuri@aerofs.com can ask about drew@aerofs.com
    - Bifrost: how to do access control checks?  Aero-Delegated-User will tell me that a request is made on behalf of drew@aerofs.com, but how do I know that drew@aerofs.com is allowed to access allen@aerofs.com's tokens because drew is an admin of allen's org?  For that, I think we have to ask Sparta (or something) if the access is allowable.
3. servers: require that requests carry one of these authorization schemes (stop accepting requests without an Authorization: header if route not meant to be public)
    - Sparta (done) ( https://gerrit.arrowfs.org/4084 ) (not all routes accept IAuthToken, but all GETs do)
    - Bifrost done( https://gerrit.arrowfs.org/4114 )
    - Verkehr (going to be hard, Verkehr has no access control logic, which will be needed to externalize)
    - Polaris (has always required authorization for all routes)
    - Auditor done ( https://gerrit.arrowfs.org/4055 )
    - config done ( https://gerrit.arrowfs.org/4001 )
    - CA (seems to be done)
4. nginx: forward all requests to backends, rather than selective proxying (basically done - finished config, bifrost)
5. clients: switch to using public routes and verify certificates
    - Java
    - Python
      - config client?
      - bifrost client
      - sparta client?

