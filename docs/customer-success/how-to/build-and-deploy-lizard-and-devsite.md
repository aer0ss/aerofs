# How-To Build and Deploy Lizard and Developer's Website

Lizard and the developer's website should be built and deployed every Monday 
- in sync with the appliance build and deploy. 

## Lizard

1. On the build machine (or another device still on Yosemite) in 
	`~/repos/aerofs/packaging` run:

        git pull
        BIN=PUBLIC make clean lizard upload
   
   A notification will be sent to the Success channel on Slack.

2. SSH into `enterprise.aerofs.com`, and run the following two commands:

        sudo su
        puppet agent -t

	If this is your first time running this, you may need to create a 
	password on the enterprise.aerofs.com box. 
	
## Developer's Website

1. Clone the developer's website repo if you haven't done so already: 
	https://gerrit.arrowfs.org/#/admin/projects/developers-website
2. Read the README and install the prerequisites if this is your first time

3. Do a `git pull` in `~/repos/aerofs/developers-website`
4. Run the following command:
        
        make clean release

5. Change your directory to `~/repos/aerofs-infra/provisioning` and run:

        ansible-playbook devweb.yml
        
   N.B. This command will **fail** until we upgrade the registry from the 
   deprecated version to version 2.
   
   **The current workaround until we upgrade the registry:
   
       ssh core@developers.aerofs.com
       ./workaround.sh
