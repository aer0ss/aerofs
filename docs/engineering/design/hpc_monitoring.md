# HPC Notifications

## Requirements

	- Monitoring HPC server cluster and displaying/notifying vital information about cluster
	capacity, RAM, CPU, Disk usage.
	- Sending appropriate notifications when:
	    - HPC server cluster is close(exact value tunable and TBD) to full capacity
	    - Deployment(s) and/or server(s) are down.

## User flow

#### Display monitoring data

	- Display monitoring data on the “Manage Servers” page in lizard.
	- Monitoring data will include:
		- Number of deployments per server, total number of deployments

#### Notifying cluster manager(s) via email

Cluster manager(s) will receive a notification whenever a threshold for number of deployments or any
other monitoring data is reached via PagerDuty.

## Design and implementation

#### New docker container per HPC server

On each HPC server, we will define a new docker container called ‘hpc-monitoring’. This container
will contain a python API that, when called, returns the RAM, CPU and disk usage of host server.

#### Interaction between lizard and monitoring container

The “Manage Servers” page on lizard will have a few new endpoints whose role will be to interact
with the monitoring container via its API.

#### Using PagerDuty for notifications

PagerDuty alerts will be setup such that they query lizard and the hpc-monitoring API
(via lizard) and send email/slack notifications to cluster manager(s) when thresholds for the
various metrics (as mentioned in the "Requirements" section of this document) are passed.

#### Deployment and Server availability

	- To check if an individual deployment and the AeroFS services within that deployment are up and
	running, we can make a GET request to https://<subdomain>.syncfs.com/admin/json-status per
	deployment.
	- To check if an HPC Server is up and running we propose adding a dedicated route to the HPC
	monitoring container API that simply returns a json signifying that the HPC Server is up and
	running. Speaking strictly, this would only mean that the monitoring container on the server is
	up and running and hence could result in false negatives i.e. only the HPC monitoring container
	is down but the server is still up. However, the monitoring container being down is seen as a
	serious enough issue to troubleshoot.

## Future implementations

- Automatic provisioning
- Changing the threshold via lizard’s interface.
