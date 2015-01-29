Team Server Sharding
===

Note: the purpose of this doc is to capture high-level and important assumptions and design decisions.

## Requirements

A main design goal is to minimize maintenance costs of a TS sharding and replicating cluster composed of tens of TSes running on commodity hardware. Because we assume small-scale clusters, a single management node (codename Athens) is allowed to simplify the design.

Not all the requirements below will be implemented in early iterations.

- TSes should continue to sync data if Athens is unavailable. The sharding function should degrade gracefully.

- A newly installed TS of an organization automatically joins that org's cluster and starts replicate data.

- A TS retires from the cluster if and only if the admin unlinks it.

- When Athens considers a TS as unavailable, it notifies the admin but does NOT re-assign shards. Reassignment only happens during TS installation and unlinking. (Integration with Pager Duty, Nagios, etc?)

- The admin can specify the *replication factor*. The factor can be either "full replication" to replicate all the data to all available TSes, or an positive integer `R` which specifies the minimum number of TSes a piece of data should be replicated to.

- When the admin attempts to unlink a TS, the system rejects the request if the number of TSes would drop below `R` after the unlinking.

- A tool is provided for the admin to ensure a TS is fully in sync with other replicas before it is unlinked.

- The admin can monitor either natively or via 3rd-party tools the Team Servers' status, shard assignment, data usage, and trends. (Integration with New Relic, Nagios, OpenNMS, Munin etc?)

## Design

- TS-Athens interaction: The TS periodically queries Athens for sharding assignments. This is also served as the heartbeat message to Athens. Athens does NOT push information to TSes.

- TS-Havre interaction: To forward API requests to appropriate TSes, the API gateway (Havre) needs to be aware of the sharding assignment. To accomplish it, TSes periodically report their responsible shards as part of the heartbeat messages to Havre. Havre does NOT communicate with Athens.

- TS implementation: The TS combines the ACL data received from SP and assignment data from Athens to determine which stores to be synced.

- Dealing with unavailability: When the Athens is unavailable (determined by one or more consecutive Athens query failures), the TS automatically syncs all the new stores created since the last successful Athens call. After Athens becomes available again, the TS deletes the stores that are not assigned to it.

- Quiesce: when Athens decides to offload a store's data from a TS, due to dynamic shard rebalancing or TS unlinking (aka retiring), Athens asks the TS to enter the quiescent period and specifies a target TS for the data. During the period the TS stops syncing from other peers and rejects API requests from Havre for that store.


### Content vs metadata sharding

- Content sharding: Stores are the unit for sharding file contents. On receiving API requests from the client, Havre extracts the Store ID (SID) from the request, and forwards the request to one TS responsible for that store.

- Metadata sharding: To move an object from one store to another, both stores need to be on the same TS to guarantee the operation's atomicity. We observe that 1) the user has no permission to move the object unless the user has access to both stores, and 2) completing the move operation only requires the presence of file metadata. Therefore, we use a different strategy for metadata, where the unit of sharding is users: if a TS is responsible for a user's metadata, the TS syncs metadata for all the stores that user has access to.

- Content migration: When moving files from store A to store B, if B is not present on the TS, the file content may be lost from the system as the files emigrate from A. To address this issue, we use the [quota system](team_server_quotas.html) to implement content sharding: the TS does not collect contents for stores that are assigned for metadata sharding but not content sharding. These stores are used to accept immigrated content so that the TS can sync such content out to other TSes.