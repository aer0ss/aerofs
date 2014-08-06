Storage Agent Requirements
===
This document outlines the requirements, the user interactions, and the externally observable behaviours of the storage agent.

Purpose
---
The primary purpose of the storage agent is to ensure that content are available. Its secondary purpose is to serve as a back-up.

Product Requirements
---
It should be simple for admins to:

* deploy
* setup / configure storage agent
* monitor agent status
* upgrade
* unlink / erase
* report a defect

The storage agent should:

* speak core protocol to other AeroFS clients & the metadata server
* report events to auditor
* listens on an exposed port for TCP-WAN traffic

Future requirements:

* determine and purge old versions of objects
* filter content through ICAP before distributing content
* support on HC => Metriks, analytics, defects, and archived logs

User Workflow
---
- the admin receives a .deb package.
- the admin starts an Ubuntu box.
- the admin installs the .deb package which installs the storage agent as a service (upstart).
- the storage agent immediately runs and fails, asking the user to setup the agent.
- the admin edits a properties file (default provided).
- the admin restarts the storage agent.
- the storage agent runs a number of sanity checks.
- the storage agent reports errors and stops immediately (if there's any).
- else the storage agent reports all status green and continues to run in the background.

Supported Operations
---

- the storage agent listens for notification in all stores it replicates.
- the storage agent listens for GetComponentRequest on all transports.
- when notified of a new store+object+version, the storage agent will eventually obtain the content for the store+object of the said _or later_ version.
- when obtained the content for a store+object+version combination, the storage agent will persist the content and notify Polaris on success.
- on receiving GetComponentRequest, the storage agent will:
    * throws an exception if the caller doesn't have ACL permission for the object+version.
    * transfer the content of the store+object of the requested _or later_ version.
- the storage agent will track each of the above events and allow additional components to interact with each event. 

Notes
---
- given a store+object+version, the content should be immutable.
- storage agent does not support user interaction at run-time.
- storage agent does not care about what's the latest version. altho determining that is cheap.

Upgrade Path
---
- WIP
- ideally the site admin doesn't need to do anything. When the appliance is upgraded, the storage agent automatically updates. (is this accurate? or does site admin want more control?)

### Options
#### Runs an auto-updater in Java (-2)
#### Runs a cronjob on the same box probing for version on the appliance (-1)
#### Runs an apt server on the appliance and a cronjob to apt-get update (0)

Unlink Path
---
- WIP
