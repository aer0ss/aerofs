##Upgrading AeroFS Production Appliance In EC2

###Part 1: Launch New Instance
1. Log into [AWS](https://signin.aws.amazon.com/oauth?SignatureVersion=4&X-Amz-Algorithm=AWS4-H  %0A
MAC-SHA256&X-Amz-Credential=AKIAJMOATPLHVSJ563XQ&X-Amz-Date=2015-07-31T22%3A28%3A26.170Z&X-Amz-  %0A
Signature=2aefe55d7ac5b98b8acab0be0fbd9c8634b584e148e37e37d7527db17e1c0aab&X-Amz-SignedHeaders=  %0A
host&client_id=arn%3Aaws%3Aiam%3A%3A015428540659%3Auser%2Fhomepage&redirect_uri=https%3A%2F%2Fc  %0A
onsole.aws.amazon.com%2Fconsole%2Fhome%3Fstate%3DhashArgs%2523%26isauthcode%3Dtrue&response_typ  %0A
e=code&state=hashArgs%23).
2. Click on **EC2**.
3. Click **Launch Instance**.
4. Click **Community AMIs** and search for the latest stable [CoreOS release](https://coreos.co  %0A
m/releases/).
5. Click **Select** next to the "paravirtual (PV)" AMI.
6. Select "m3.medium" as the instance type.
7. Click **Next: Configure Instance Details**.
8. For "Network" select "production". For "Subnet" select "public-production". For "Auto-assign
Public IP" select "Enable".
9. Click **Advanced Details**.
10. Copy/paste the contents of the [cloud-config.yml](https://github.com/aerofs/aerofs-docker/b  %0A
lob/master/cloud-config.yml) file into the "User data" field as text.
11. Click **Next: Add Storage**.
12. Set "Size" to at least 50GB.
13. Click **Next: Tag Instance**.
14. Name the instance as `[<your_name>] share.aerofs.com appliance <appliance_version>`.
15. Click **Next: Configure Security Group**.
16. Click on the "Select an existing security group" radio button, and select the "aerofs.appli
ance" security group.
17. Click **Review and Launch** then hit **Launch**.
18. Select a key pair or create a new one. If creating a new one, enter your name for the key n
ame and save it to `~/repos`. Select the appropriate key pair and hit **Launch Instance**.
19. Your instance should now appear in the EC2 Dashboard as a running instance.

###Part 2: Backup and Restore
1. Go to `share.aerofs.com:8484`, upload license file, click **Backup and upgrade** and hit **D
ownload backup file and put appliance in Maintenance Mode**.
2. Save the backup file in a safe place and shut down the old running instance of the appliance.
3. Point browser to the private IP address of the instance launched in Part 1.
4. [Restore the appliance](https://support.aerofs.com/hc/en-us/articles/204631424-How-Do-I-Upgr
ade-My-AeroFS-Appliance-) using the backup file downloaded in Part 1.


###Part 3: Update DNS Records and Populate Public Keys

####Update External DNS Record
1. Click on **Route 53** form the AWS Console Home (Cube button in top left corner).
2. Click **Hosted Zones** > **aerofs.com**.
3. Select "share.aerofs.com" and update the IP value to the public IP of the new instance.
4. Hit **Save Record Set** (Note that it takes about 30 mins for this change to take effect).

####Update Internal DNS Record
1. cd into the *aerofs-infra* repo and do a `git pull`. If you don't have this repo, you can ge
t it [here](https://github.com/aerofs/aerofs-infra).
2. Run the following commands and replace the IP address for "share.aerofs.com" with the new pr
ivate IP address.

        opshell/run
        vi roles/aerofs.vpn/files/vpn.hosts
        ansible-playbook apps.yml -l vpn.arrowfs.org -K -u <userid>
3. Push the changes to 'vpn.hosts' file to git

####Poplulate Engineers' Public Keys

```
ansible-playbook --private-key /repos/<key_name> -l share.aerofs.com keys.yml
```
        
where <key_name> is the name of the key file you selected in part 1, step 18.

**Note:** If you have `ssh-agent` and it doesn't work inside opshell, exit out of the container
 (CTRL+D) and do the following:

```
unset SSH_AUTH_SOCK
opshell\run 
ansible-playbook --private-key /repos/<key_name> -l share.aerofs.com keys.yml
```
