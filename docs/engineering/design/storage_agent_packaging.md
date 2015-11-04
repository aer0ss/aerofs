# Storage Agent Packaging

## Requirements

- The storage agent will run as a docker container(s) on a linux appliance.

- The storage agent will run independently from the AeroFS appliance i.e. in a separate VM in
preferrably a different host. This will achieve separation between the AeroFS appliance and
content being shared via AeroFS.

- The Storage agent will support block storage *only*, with 3 backend options:
    1. Local block
    2. S3
    3. Openstack/Swift

- The storage agent must be authenticated against the appliance. Only an org admin can download
and run the storage agent.

## Desired setup experience for admins
1. Admin downloads the storage agent appliance(OVA, QCOW2, VHD) from https://privatecloud.aerofs.com
2. Admin completes download of the storage agent appliance and starts it.
3. Admin is shown an IP address in the appliance console to setup the storage agent.

(These steps are in line with the experience an organization admin goes through when downloading
and setting up the main AeroFS appliance.)

##### Storage agent setup flow

On navigating to the IP address from step 3 above, the admin will go through the following
steps to complete setup:

1. On page 1, admin will be promted to go through to the following steps in sequence:
    - Download a "storage agent config bundle" from appliance bunker page(instructions provided).
    Details of config bundle follow in later sections.
    - Upload config bundle downloaded in previous step.
    - Enter storage backend information.
    - Clicking a "next" button that loads page in step 2 on successful setup or re-prompts for
    erroneous information.
2. Page 2 permanently displays:
    - Link to a new bunker page that will contain monitoring stats for this storage agent.*(Future)*
    - Upgrade button.
    - Re-setup button if admin chooses to do so.
3. Successful completion of the above steps will automatically start the storage agent.

## Design and implementation

##### Storage agent config bundle

- The storage agent config bundle will consist of:
    - site-config.properties or similar file with appliance address and base CA cert.
    - A special token that the appliance will use to authenticate a storage agent, unique per
      storage agent.

##### Storage agent auth token

- Auth token that verifies client being setup is infact an authorized storage agent.
  Supplied to the appliance by the storage agent web interface as authentication proof.
- A new table in SP will be used to store all storage agent auth tokens. This table will also
  contain other relevent details such as id, creation time, expiry time,
  associated DID (initially null) along with the token itself.
- A new token is created per new storage agent. (in step 1 of "storage agent setup flow").

##### Containerization

- The storage agent appliance will ship with 3 docker containers:
	- The loader that brings up the other 2 containers.
	- The setup container that will host the setup web app. This is the interface admins will use to
    start setup. On successful setup, the container will signal the loader to start the core
    storage agent container.
	- The core storage agent container is responsible for syncing content.
- On initial startup of the appliance, the appliance will start in  "maintenance" mode i.e.
only the setup container will be brought up by the loader. The admin will be prompted to complete
setup.
- The setup container and storage agent container will both mount the same directory (/aerofs) from
the host vm as a data volume that will store all persistent data under it. This includes the root
AeroFS directory for the storage agent and the rtroot. Everything under this directory will remain
persistent across restarts and upgrades.
- Successful completion of setup will:
	-  Create the following two directories:
		- /aerofs/AeroFS - This is the root AeroFS directory where all the data will be synced to.
		- /aerofs/.aerofs-storage-agent -  This is the rtroot.
   - Start the core storage agent container.

##### Mutual auth between AeroFS appliance and storage agent setup container

- Clicking "next" on page 1 of setup as mentioned in step 1 of "Storag agent setup flow" will:
    - Initiate mutual authentication between AeroFS appliance and storage agent setup container.
    The appliance is authenticated using the base CA cert in site-config properties and the setupi
    container is authenticated using the auth token.
    - Register the storage agent device. The setup container will provide a CSR for signing and
    persist signed cert in the rtroot (along with the private key).

####Upgrades

- In place upgrades to the storage agent will be initiated by the admin clicking on a button
exposed to him/her in step 2 under "Storage agent setup flow"
- Clicking the button will initiate the loader to check against registry for upgrades.
- In the event of an upgrade the loader container will destroy the other containers including
itself and start the upgraded loader. The upgraded loader will bring up the other containers.
- *In the future* we will want the storage agent to get upgraded along with the appliance.
