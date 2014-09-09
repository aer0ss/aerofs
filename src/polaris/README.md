# Baseline

Baseline is a HTTP/REST/JSON framework heavily influenced by DropWizard.
It bundles together:

* Jersey 2.x
* Netty 4.x
* Jackson 2.x
* JDBI 2.x
* Metrics 3.x

# Polaris

Polaris is the AeroFS centralized metadata server. It is built on top of baseline.

# Bugs

* Fix how exceptions are printed for configuration and YAML parsing
* Resource methods can be called with a null parameter even if the parameter is not explicitly marked @Nullable
  (should throw bad request within Jersey pipeline if parameter is not explicitly nullable)
* ExceptionMapper does not have access to the request
* Validate JSON objects
* Remove getXXX and setXXX for configuration objects
* Separate baseline db code from non-db code
