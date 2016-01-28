# How-To Build and Deploy Prod

This process generates the following signed artifacts:

1. On S3:
    - Signed Appliance OVA
    - Signed Appliance QCow2
    - Signed Appliance VHD
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

4. Make and push messaging images to the Registry. To do this, run the
   following commands:

        ~/repos/aeroim-client/bin/aero web build
        ~/repos/aeroim-client/bin/aero web publish
        
5. Package the AeroIM installers into the appliance by running:

        ~/repos/aeroim-client/bin/aero desktop build <appliance_version_to_build>

6. Build using:

        invoke --signed clean proto build_client package_clients build_docker_images build_vm

   Note ([*FIXME*](https://aerofs.atlassian.net/browse/ENG-2455)): during the
   build_vm phase, ssh onto the ship-enterprise builder using the ssh command
   provided in the execution output. Then run `top -d 0.1` to speed up the
   docker pull.

7. [QA the VMs](../testing/private-cloud-manual-test-plan.html)

8. When you are ready to release,

        invoke push_docker_images push_vm tag_release

   This will upload the artifacts to S3 and the docker registry and send
   corresponding slack notifications. The docker registry images will be
   available to the public immediately. The S3 artifacts need to be released
   manually by updating the version on the
   [Private Cloud Admin Portal](http://enterprise.aerofs.com:8000/release).

   If for any reason, a release needs to be pulled from the registry,
   tag the loader that should be the correct release with the tag
   latest. Use the following command to list the loader tags to find the correct loader
   image you want to give the lastest tag:

         curl -s https://registry.aerofs.com/v1/repositories/aerofs/loader/tags
         | python -m json.tool

   Then run,

         curl -v -X PUT https://registry.aerofs.com:5050/v1/repositories/aerofs/
         loader/tags/latest -H "Content-Type: application/json" -d
         '"<loader image you want to roll back to>"'

   The loader image will now be set to latest.

9. After releasing, write the
[Release Notes](https://support.aerofs.com/hc/en-us/articles/201439644-AeroFS-Release-Notes),
the Internal Release notes for product updates released only to the AeroFS team
(Slack channel "Internal Releases"), and go through the JIRA ENG sprint report to take note of
the bug fixes going out in the release. The ENG tickets for the bug fixes should be mapped to
Zendesk tickets with their respective asignees. Notify members of the Customer Success Team who
should follow-up with their customers to inform them of the bug fix.


10. [OPTIONAL] Reset your docker-dev RAM.

        dk-halt
        # Stop the VM in the VirtualBox GUI, and adjust RAM to 3 GB.
