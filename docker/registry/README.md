A *Ship Enterprise* appliance for the AeroFS docker registry at registry.aerofs.com.

The appliance's port 443 should be open for public read-only access. Port 5050
should be open only within the AeroFS VPN for internal read-write access. Both
ports uses HTTPS.

# Set up S3 and HTTPS certificate and keys

The registry uses S3 to store image layers. The S3 bucket settings can be found at "registry/root/run.sh".
 
The HTTPS certificate must match the CName "registry.aerofs.com". Ask Yuri to issue new certificate as needed.

The S3 and HTTPS secret/private keys must to be copied to the "keys" folder *before* you can build the system or test it
in local environment. They should match the S3 key and HTTPS cert files already in that folder. 

    $ cp path_to_aws.secret path_to_nginx.key keys

Where "registry.aerofs.com" should match the S3 bucket name specified in "registry/root/run.sh".

# Build the system

The system contains a bunch of containers (use `list-containers.sh` to list them) and a cloud-config file. To build 
them using Ship Enterprise:

    $ ./build.sh

The script specifies `cloudinit` as the only output format. No appliance VMs will be generated. 

# Deploy and upgrade the system

To push all the built images to Docker Hub and release and new version:

    $ echo {new_version} > loader/root/tag
    $ ./build.sh
    $ ./push-images.sh

Remember to update the tag file otherwise existing versions on Docker Hub will be overwritten!

Then, [launch a CoreOS VM in any clouds](https://coreos.com/docs/#running-coreos). Supply the VM with the cloud-config
file generated in the build step.

Set security rules such that port 443 is open to public access and 5050 is open to VPN access only.

*Security notice*: The cloud-config file contains S3 and HTTPS secret/private keys. Do NOT distribute the file!
 
Alternatively, use the upgrade feature of Ship Enterprise to upgrade an existing VM in place.

# Run the system locally

To run the system in the local development environment:

    $ ./build.sh
    $ export KEYS_DIR=$PWD/keys
    $ crane run
     
See crane.yml for more information on the setup of the system.
