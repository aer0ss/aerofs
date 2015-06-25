# How-To Build and Deploy Prod

This process generates the following signed artifacts:

1. On S3:
    - Signed Appliance OVA
    - Signed Appliance QCow2
    - Signed Appliance VMDK
2. On the docker registry:
    - Signed Docker Images

All of the above artifacts will share the same version. The version is computed
using the latest version on the docker registry + 1. To specify a manual
version, use `--release-version` in step 4 below.

## Procedure

1. [OPTIONAL] If you have 8 GB RAM, you should downgrade the RAM on your
   docker-dev VM to 1 GB instead of 3 GB for the purpose of this build. On 16
   GB Macs this should not be necessary.

        dk-halt
        # Stop the VM in the VirtualBox GUI, and adjust RAM to 1 GB.

2. Ensure your docker-dev machine is up and running by executing the following:

        dk-start-vm
        ./tools/cache/start.sh

3. Obtain the signing key truecrypt volume `aerofskeys.truecrypt` (used for
   executable signing) and mount it. You will need the mount password. Ask
   Matt. After mounting, open keychain and do a File > Add Keychain... and
   select the `aerofs.keychain` file which you just mounted. Press the little
   unlock button and enter the keychain password (which is _not_ the same as
   the mount password). For this, also ask Matt.

4. Build the S3 artifacts using:

        invoke --signed clean proto build_client package_clients build_docker_images build_vm

5. *QA the VMs* (exact process documentation coming soon).

6. When you are ready to release,

        invoke push_docker_images push_vm tag_release

   This will upload the artifacts to S3 and the docker registry and send
   corresponding slack notifications. The docker registry images will be
   available to the public immediately. The S3 artifacts need to be released
   manually by updating the version on the
   [Private Cloud Admin Portal](http://pc.aerofs.com:8000/release).

7. [OPTIONAL] Reset your docker-dev RAM.

        dk-halt
        # Stop the VM in the VirtualBox GUI, and adjust RAM to 3 GB.

