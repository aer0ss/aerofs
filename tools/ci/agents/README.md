This directory contains dockerizes TeamCity agents. Admins can now easily set up agents on any VMs.

The new agent doesn't support SyncDET tests yet. TeamCity only runs unittests on it for now.

# Set up a new agent

The agent requires two or more CPUs otherwise some unittests may fail. `scp` this directory
to an empty VM or physical computer and run on it:

    $ <this-folder>/start-teamcity-agent-container.sh

In a few minutes after the script finishes, TeamCity should show a new agent named "cloud-1".

For troubleshooting, see `teamcity-agent` container's docker logs.

# Future work

- Things like the agent's port and name are hardcoded. We should dynamically assign them in the future.

- All the agents should be eventually dockerized and provisioned in the cloud.

- The agent image can be re-purposed for developers' local builds. 

