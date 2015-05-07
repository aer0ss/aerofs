This directory contains dockerizes TeamCity agents. Admins can now easily set up agents on any VMs.

The new agent doesn't support SyncDET tests yet. TeamCity only runs unittests on it for now.

# Set up a new agent

`scp` this directory to an empty VM and run:

    $ <this-folder>/start-teamcity-agent-container.sh

on the VM. In a few minutes after the script finishes, TeamCity should show a new agent named "cloud-1".

For troubleshooting, see `teamcity-agent` container's docker logs.

# Future work

- Things like the agent's port and name are hardcoded. We should dynamically assign them in the future.

- All the agents should be eventually dockerized and provisioned in the cloud.

- The agent image can be re-purposed for developers' local builds. 

