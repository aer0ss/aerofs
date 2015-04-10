Background
---

The SSL certificates used on most AeroFS services are good for a year, and that
means certificates expire all the time and throughout the year.

This document describes how to renew a certificate.

Steps
---

- When a certificate expires, the most likely and immediate observation is the
  corresponding PagerDuty probe firing.

  If so, the first step is to acknowledge the incident and PagerDuty and
  schedule a service windows so the probe doesn't fire again while we are
  renewing the ceritificate.

- Verify it's actually the end-entity certificate that's expired by the
  following methods:

  - Connect using a SSL client: `openssl s_client <machine name>:<port>`

  - Examine the certificate by SSH into the machine, locate the certificate,
    and inspect the certificate, e.g. `sudo openssl x509 -in
    /etc/nginx/certs/sp.cert -text -noout`.

- Now that we've confirmed the certificate has expired, we need to generate a
  new certificate.

  SSH to `joan.aerofs.com` and run `crt-create`, eg. `crt-create
  sp.aerofs.com`. This produces a folder with new key and certificate.

- Copy the certificate and key to `puppet.arrowfs.org` in `/data/aerofs_ssl/`
  with the appropriate name, eg. `sp.key` and `sp.cert`.

- SSH into the service again and run `sudo puppet agent -t` to force a puppet
  run and grab the new key and cert from the puppet master.

- Verify all is well and you are done.

- Close the maintenance window on PagerDuty if you scheduled one earlier.
