# Setting up Build Machine for TeamCity - Mac Edition

Currently, we are building a signed Appliance every night at midnight. A Macbook has been 
provisioned as a TeamCity Agent to execute the build. It is done on a Macbook because only Mac OS
can sign our Mac installers.

This how-to will illustrate the process of setting up a Mac machine to work nicely with TeamCity 
in the event that you have to provision a new machine.

### Pre-requisite
Setup Tunnelblick so that the build machine can connect to AeroFS VPN. Matt should be able to setup
a new profile.

In order to sign our installer, we have to mount our signing keys via TrueCrypt. Please get
a copy of the signing key from Matt. He will also be able to provide the password to unlock it.

### Deploying Ansible Playbook

1. Enter `git clone git@github.com:aerofs/aerofs-infra.git` to clone the aerofs-infra repository.

2. Obtain the machine's IP address (VPN) by enter `ifconfig`. The IP address of interest should
be located under `utun0` interface. Add the IP address to 
`provisioning/roles/aerofs.vpn/files/vpn.hosts`. Associate the IP address with a hostname of your
choice

	e.g. buildmachine

		172.19.10.58  buildmachine.arrowfs.org

3. Update `provisioning/hosts/static` to include the build machine host. It should be added below
`servers` and `docker-non-coreos`

	e.g. 
		
		[servers]
		buildmachine.arrowfs.org ansible_ssh_user=buildmachine hostname=buildmachine.arrowfs.org

		* Where ansible_ssh_user is the username of the mac machine

		[docker-non-coreos]
		buildmachine.arrowfs.org

4. Update new directives in `provisioning/build-ci.yml` to build CI for your new machine. If you 
are replacing the current build machine, simply update `hosts` with the new name.

	e.g.

		- name: Set up build machine on ci
  		  hosts: buildmachine.arrowfs.org
          roles:
    		- ci.common
    		- ci.buildmachine

5. Run `opshell/run` to start provisioning your build machine.

6. Deploy vpn app: `ansible-playbook apps.yml -l vpn.arrowfs.org -K -u <userid>`. `<userid>` 
refers to the account id you use for your computer.

7. Deploy the team's ssh key: `ansible-playbook keys.yml`

8. Execute TeamCity script: `ansible-playbook build_ci.yml`

 	* Running the above playbook command will install a script to your build machine.
	  The script will be located on the home directory (~) of the build machine. If for some 
	  reason the machine crashes, re-run the script with:

		```
		./run-teamcity-agent-background.sh <Agent Name> http://libellule.arrowfs.org/ 9091 &
		```

		`<Agent Name>` is currently labelled as `buildmachine-agent`. If in the future, you would
		like to change it, please edit `provisioning/roles/ci.buildmachine/defaults/main.yml`
		
### Setting up Team City

1. Before configurating your machine to work with Team City you must have a Team City Account with 
`System Administrator` access. Be careful what you change because this is the highest permission 
level for Team City. Hugues can provide you with an account and the permission required.

2. For each Team City Agent, you will need a license. If it is currently at full capacity, ask Yuri
to purchase additional licenses.

3. After acquiring a license, you can `Authorize` your device under 
`Agents > buldmachine-agent > Authorize`

4. Finally, configure the Agent to only build the signed appliance by changing the
`Compatible Configuration` and set the following. 
	
		- Current run configuration policy: Run assigned configuration only
		- Select AeroFS::Signed
