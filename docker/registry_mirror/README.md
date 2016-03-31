**Registry Mirror**

A registry mirror could potentially be used by our customers to mirror registry.aerofs.com
so that they don't have to expose their appliance to the internet. They instead deploy a
server that can run a OVA/cloudinit that we provide them with that can mirror registry.aerofs.com

**How to deploy registry mirror in production**

1. When a customer wants to deploy a registry mirror they just have take the OVA/cloudinit that we
provide them with and deploy it on a VM or server.

2. The customer can setup the registry mirror with SSL using:
    - Their own certificate in which case the hostname of the server hosting the registry must be
      the same as the CN name of the certificate.
    - A self signed certificate using the following steps:
        - SSH in the registry mirror VM
        - Run following cmd:
           mkdir -p certs && openssl req \
             -newkey rsa:4096 -nodes -sha256 -keyout certs/domain.key \
               -x509 -days 365 -out certs/domain.crt
         The days value can obviously be modified according to their preferences.
    - No matter which certificate the customer uses THEY MUST copy the certificate and key into
      the /nginx-auth directory. PLEASE NOTE they must use the same name i.e. domain.key and
      domain.crt in the /nginx-auth directory too.
      So for example if creating a self signed cert from the step above, the customer
      would do the following:
            cp certs/domain.key /nginx-auth/
            cp certs/domain.crt /nginx-auth/
     And if using their a trusted certificate they would do:
            cp <some-trusted_cert> /nginx-auth/domain.crt
            cp <some-trusted_cert's_key> /nginx-auth/domain.key

   - Reboot the registry mirror VM.

3. IF USING SELF-SIGNED CERTIFICATE: In their appliance they must add their certificate from
   the previous step(self signed or otherwise) to an appropriate location which might
   vary according to the appliance's host OS.
   For instance on a coreOS VM they should create a file at path:
        /etc/docker/certs.d/<domain_name>/ca.crt
   and cp their cert into that file and reboot their appliance. N.B. they will likely have to create
   the <domain_name> directory under /etc/docker/certs.d too.

**How to build registry mirror**

1. ./build.sh

The above steps should build a cloud-config/preloaded/cloud-config vm.

2. If you want to push changes to the repository:

./push-images.sh


