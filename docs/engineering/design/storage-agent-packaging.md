# Storage Agent Packaging

## Requirements

- Appliance is renamed to "Application Server" with optional "onboard storage"
in the form of a storage agent
- Standalone Storage Agent will be called "Storage Server"

- The storage agent container(s) will be bundled into the Application Server
- A separate VM for the Storage Server will 
be available from privatecloud.aerofs.com
- The Application Server will have 2 modes:
    - "with on board storage", where both the appliance and SA will run *(default)*
    - "dedicated application server", where the SA doesn't run
- The Storage Agent will support block storage *only*, with 3 backend options:
    - Local block
    - S3
    - Openstack/Swift

#### Onboard Storage Agent setup flow

After the initial appliance setup finishes (i.e. outside of maintenance mode),
      users are given a menu to change what mode the application server runs in

- If they choose to run a dedicated application server, continue to bunker 
as normal.
- If they choose to run with onboard storage, leads them to setting
up the SA's storage settings.
- After setting up storage settings, the storage agent starts and the user is
returned to the bunker status page
- *No config bundle is required for the onboard storage agent*

#### Storage Server setup flow

Admins download a separate VM from privatecloud.aerofs.com (or use a
cloud-config specific to the SA containers) Once the virtual machine
boots, it displays an IP for the admins to continue with setup. Networking
settings and a root shell are also available from this console.

On the website:

0. Admins are prompted to upload a setup bundle from the appliance
0. Admins go to bunker, where there is a "setup a storage server" page
0. there, admins will configure the new storage agent's settings
   (storage options), and get a bundle to download. this page should have
   immediate feedback (but overridable) if remote storage options don't work
0. admins upload the bundle to the storage agent's setup portal, click next,
   receive feedback if setup was successful or if storage options don't work
0. if setup was successful, the storage agent is started and the admin is shown
   a "storage server is running" page. page contains links to re-setup storage
   agent (possible data loss, needs warning modal) and in-place upgrade


#### Other changes

Several pages related to storage agent setup will be added to bunker:

- monitoring stats for onboard/offboard storage agents.*(Future)*
- Toggle on/off onboard storage agent page
- Re-setup storage options of onboard storage agent
- A button to clear onboard storage agent data (with warning modal)


#### Toggling Onboard Storage Agent

The onboard storage agent can be toggled on and off any number of times.
Turning the storage agent on and off should not cause any of the storage agent's
state/data to be lost.

#### Points of concern

- SA files will be included in the appliance backup file. To avoid downloading a
large backup file, there will be a warning modal upon backing up if there is a
large amount of storage agent data.
- Duplication of in-place upgrade pages between bunker and SA web setup
- SA setup will need to be made after the appliance finishes setting up, as it
depends on sp

#### Points to explore

- If transition from a SA/Appliance combination, the SA files should not be
persisted in the backup and backups to come. Ideally they'd be deleted from the
disk once the decision to move to a lone appliance were made.
- SA setup container will need to tell if other appliance containers are running
or if it is running as standalone SA (either conditional on docker links being
present, or have two slightly different images)


## Design and implementation

##### Backup file
- Bunker will need access to the storage agent's data volumes, to add them to
backup file
- Show warning modal if SA files are above a certain size (1 GB) to warn of a
slow download, give option to continue or not include SA files in the backup
file
- Do **NOT** include SA files in the backup file if onboard storage agent is
disabled

##### Storage agent config bundle

The storage agent config bundle will consist of:

- site-config.properties or similar file with appliance address and base CA
cert.
- A special token that the appliance will use to authenticate a storage agent,
    unique per storage agent.
- storage-agent.config that specifies what storage options the storage agent
will use

##### Storage agent auth token

- Auth token that verifies client being setup is in fact an authorized storage
agent.  Supplied to the appliance by the storage agent web interface as
authentication proof.
- A new table in SP will be used to store all storage agent auth tokens. This
table will also contain other relevant details such as id, creation time, expiry
time, associated DID (initially null) along with the token itself.
- A new token is created per new storage agent.

##### Appliance Modes

- The new modes of the appliance will be implemented using new crane groups
- May need multiple aliases for the same image given that there are different
container dependencies between different modes

##### Standalone Storage Agent VM

- Offboard storage agent VM will use same build system as appliance
- same loader infrastructure with different crane.yml
- for development workflow, new set of aliases to use the new yml file
- during development runs as separate containers within docker, acts the same
as if containers were running on a separate host

##### Containerization

- The appliance will ship with the core storage agent container that syncs
content
- The VM for an offboard SA contains the core storage agent container, the
loader, and the setup container that has the web portal for admins
- Bunker will be modified to also contain the storage agent setup flow used when
the appliance has an onboard SA
- The setup container and storage agent container will both use the same volume
`/aerofs-sa` to pass data between the two. Also means that data in this volume
persists across upgrades. Volume will need to be accessed by bunker to make a
backup file.
- Successful completion of setup will create the following two directories in
the volume:
    - `/aerofs-storage/AeroFS` - This is the root AeroFS directory where all the data
    will be synced to. If admins want to sync data to a NAS, this is where they
    should mount it.
    - `/aerofs-storage/.aerofs-storage-agent` -  This is the rtroot.

##### Mutual auth between AeroFS appliance and storage agent setup container

Clicking "next" as mentioned in step 4 of "Storage Server setup flow"
will:

- Initiate mutual authentication between AeroFS appliance and storage agent
setup container. The appliance is authenticated using the base CA cert in
site-config properties and the setup container is authenticated using the auth
token.
- Register the storage agent device. The setup container will provide a CSR for
signing and persist signed cert in the rtroot (along with the private key).

##### Upgrades

- In place upgrades to the storage agent will be initiated by the admin clicking
on a button exposed in the storage agent web interface. In-place upgrade is the
same as on the appliance.
- *In the future* we will want the storage agent to get upgraded along with the
appliance. Or upgrades to be kicked off through bunker.


##### Alternatives

- Make the SA's docker images, the loader, and ship/sail available an a tgz.
Docker can load images from a local source, though it's a slightly new workflow
for us. Admins will need to set up the loader to be run as a service someow.
- Make the _contents_ of the SA container and its setup container available to
download as a tgz run the executables directly. Setting up the host that will
run these executables will be more involved, since it'll need to replicate the
functionality of our Dockerfiles. Also, no live upgrade which is a big drawback
for a SA with lots of synced files.
- Make a cloud-config.yml available for just the storage agent containers, with
a completely separate crane.yml. Will not work out of the box for customers who
don't want to connect to our public docker registry. For these customers it
should be relatively easy to alter the cloud-config file though. Problem will be
that other customers will ask for prebuilt VM images, meaning additional uploads
needed.
- Ship SA as a linux-only client, use existing client auto-upgrade support. Will
require extra complexity to also run the SA setup website. Also might not be
desirable to have upgrades not initiated by the admin, and appliance will need
to host the client.
