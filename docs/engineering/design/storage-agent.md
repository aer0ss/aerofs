Storage Agent Design
===

Design Principles & Decisions
---
* Aim for a high level of OS integration.
* Re-use existing components

Data Model
---
- A storage agent tracks content for the store+object+version tuples in interested stores.

Technical Components
---

### Core
These will be built for the MVP.

#### Core Event Queue, Executor, Database, & Locks
* Re-use.
* Need to include TCB, Token, TokenManager, & ExponentialRetry et al.

#### VerkehrClient
* Re-use.
* Listens in on interested stores.

#### AntiEntropy
* Re-use.
* Tracks known store+object+version and generate GetComponentRequests.

#### Collector
* Re-use.
* Queries Polaris and make GetComponentRequests.

#### GetComponentResponse handler
* Persist content to storage and notifies Polaris.

#### GetComponentRequest handler
* Verifies permission and transfers content.

#### ACLSynchronizer & ACLNotificationSubscriber
* Re-use
* Keeps ACL in sync.

#### Block Storage
* Re-use (with modifications)
* Supports at least Local & S3.
* Configurable via a properties file.

#### Database
* Need to track all local versions.
* Need to track KML.
* Need to track ACL.

### Transports
* LAN
* Zephyr
* TCP-WAN

### Future Core Components
These will not be built for the MVP, but should be included in the first release.

#### Quota / Storage Management
* Evict content based on user quota.

#### Auditor Client
* Tracks all successful GetComponentRequests
* Tracks all successful GetComponentResponses

#### ICAPP Client
* Filter content through an ICAPP server before notifying Polaris

#### Delete Old Versions of Objects
* Storage Agent will determine what versions of an object to delete and notify Polaris.

### Auxillary Components
These will not be built for the MVP, but need to be included in the first release.

#### Packaging
* Only support Ubuntu.
* Package as a .deb package.
* Runs as an upstart service.
* Runs as a single process. The service should be fail fast and let upstart handle monitoring status & restart.
* The upstart task will first perform sanity checks (and aborts if checks failed) and then runs the service.

#### Shell
* Entry point to the service.
* Manages the launch sequence.
* Supports a dry-run to perform only sanity checks.

#### Setup
* Runs on start, do the following if the storage agent is not setup.
* Authenticates the user and registers the device.
* Generates a cert and makes a CSR.
* Create an environment that core requires (approot, rtroot, cfg, etc.)
* Restarts the storage agent after setup.

#### Recertify
* Runs on start, repeats periodically, and keeps device cert up-to-date.

#### SanityPoller
* Runs on start and repeats periodically.
* Checks disk for read access, write access, and available disk space.
* Checks S3 access when using S3 as storage.
* Logs errors when encountered.

### Future Auxillary Components
When we support storage agent for HC in the far future, we'll need defects, logs, metriks, and analytics.

Unknowns
---
* How is the Polaris + Storage Agent + Clients going to handle migrating object across stores? what's the data model for the whole system?
* Is block storage only keeping the latest version (current impl.) or keeping all version (sans deletion)
* Database schema.
* Impl. details of the auxillary components.
* Upgrade path.
* Unlink path.
* A complete set of all supported properties.
* How to provision appliance URL & cert.

Interfaces
---

### Storage agent core to other AeroFS client cores
* Core protocol.

### Shell to storage agent core
* Shell launches the core.
* Shell provides a signed device cert, approot, rtroot, initialized loggers, and all auxillary support.

### Admin to storage agent
* Authenticate somehow.
* Provide configuration properties using multiple properties files (system default + user input).

### Admin to storage agent at run-time
* Admin may not interact with the storage agent at run-time.
* Admin may monitor storage agent status as reported by upstart.
* Admin may monitor storage agent logs for errors.

Test Plans
---
* Unit tests & system tests using Syncdet.
* Manual test for packaging.
