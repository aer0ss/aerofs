This directory contains dockerizes TeamCity agents. Admins can now easily set up agents on any VMs.

The new agent doesn't support SyncDET tests yet. TeamCity only runs unittests on it for now.

# Set up a new agent

- The agent requires two or more CPUs otherwise some unittests may fail.

- Have the host's DNS nameserver point to vpn.arrowfs.org's IP. For CoreOS use the cloud-config.yml in this folder 
and reboot the VM to have the DNS settings take effect. TODO (WW) cloud-config should auto restart systemd-resolved. 

- `scp` this directory to an empty VM or physical computer and run on it:

    <this-folder>/start-teamcity-agent.sh agent-1 https://ci.arrowfs.org

  In a few minutes after the script exits, TeamCity should show a new agent named "agent-1".

- If an agent is used to launch AeroFS appliance containers, specify the option `no-unittest-services` to the script
otherwise unittest services including ejabberd and mysql would occupy the ports the appliance listens to.

# Future work

- Things like the agent's port and name are hardcoded. We should dynamically assign them in the future.

- All the agents should be eventually dockerized and provisioned in the cloud.

- The agent image can be re-purposed for developers' local builds. 

