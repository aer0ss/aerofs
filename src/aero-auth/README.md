# aero-auth

Contains HTTP header/value definitions, baseline Authenticator implementations and HK2 binders
to support the following AeroFS-defined authentication schemes:

* Aero-Device-Cert
* Aero-Delegated-User-Device
* Aero-Service-Shared-Secret

## TODO

* Create Apache HTTP client helper classes (AuthScheme, CredentialsProvider, etc.)
  that we can use instead of adding headers manually