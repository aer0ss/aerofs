How to Setup and Run Scalability Tests in Amazon EC2
====================================================

# Setup And Running basic multi-clients Scalability tests in EC2

## 0. EC2 VPC and subnet and other helpful suggestions.

You should use the  existing test vpc (test-vpc: vpc-54fb1231) and subnet: subnet-3927244d (default security group is open to the world) or create your own
Make sure when you launch your appliance, team server and client instances to use the same subnet.

Having AWS console access can also be useful. Ping Matt or Drew for AWS console access.

######Note
This whole process is pretty dependent on the web signup flow. If anything changes in the web signup flow this doc(especially the tools/sctest/signup.sh script is pretty useless). So make sure you ask Abhishek or someone familiar with the flow about if that is script is still useful or not. The hope is in the future the scalability tests will be more automated and the user will not have to worry about the flow changing.

## 1. Setup Appliance

##### 1) Creating Appliance Image

Create appliance EC2 image following the steps here:

https://support.aerofs.com/hc/en-us/articles/204861900

**Useful tips while creating appliance EC2 image.**

* Get the latest appliance qcow2 image from Jon Pile or bake one. Again ask Jon or Drew.
* As mentioned before, we recommend setting up all EC2 instances in VPC and assigning elastic IP to your appliance instance so DNS name is permanent. Make sure all necessary ports are open (for testing purposess you can use security group "open" so everything is open to the world)

   This can be done while importing/creating the instance by using:

   		--subnet subnet-3927244d and -z us-east-1c

** Note: Please refrain from finishing appliance setup till you are done with Step 2 **

#####2) SSH Into Appliance
** Create your key pair by selecting Key Pairs from the list of the left hand side of the AWS console.
You can create the private key either through the AWS CLI by taking a look at the AWS EC2-create-keypair API or through the AWS console. **

** Note: you can also import your RSA key if you'd like **

To SSH into your appliance EC2 instance, you will have to launch another dummy ubuntu instance.

Follow these steps in order:

1. Create a dummy ubuntu instance (using any specification and your key pair. The option to enter the key pair doesn't appear until you click the launch button.).
2. Shut down the original appliance EC2 instance.
3. Detach /dev/sda1 from the appliance EC2 instance.
5. Attach original appliance EC2 instance's to dummy instance as /dev/sdf, and mount it (mount /dev/xvdf1 /mnt -t ext4)
6. Add your  ssh key (from ~/.ssh/id_rsa.pub of your laptop) to /mnt/home/ubuntu/.ssh/authorized_keys
7. Unmount(umount) /dev/xvdf1 from dummy instance instance , detach it from the instance, and attach back to appliance instance as /dev/sda1.
8. Start appliance instance.

Start an EC2 instance based on appliance image and ensure you can ssh to it.

    ssh -i ~/.ssh/YOUR_EC2_PRIVATE_KEY.pem ubuntu@EC2_APPLIANCE_PUBLIC_DNS

You can get the EC2_APPLIANCE_PUBLIC_DNS by logging onto the AWS console and checking for it there.


#####3) Setup appliance following regular setup steps in browser.
* Hostname
 	* You can use the public DNS of your appliance EC2 instance but please note that this(as of right now) will require you to change your ejabberd configuration in the appliance EC2 instance. To do this:
 		* SSH into your appliance EC2 instance(from Step 2)
 		* sudo vim /etc/ejabberd/ejabberd.cfg
 		* Under enabled hosts:

 				{hosts, ["whatever it is right now"]} -> {hosts, ["amazonaws.com"]}
 	    * Restart ejabberd service

 * Create your first user adminuser@aerofs.com. Give it any password you want but use the same one for Step 3 of Setup Team Server.

** Steps 4 and 5 are to be carried out in the appliance EC2 instance after ssh'ing into it.**

#####4) Enable open signup and restart tomcat:

    sudo vi /opt/config/templates/server.tmplt => set open_signup=true
    sudo service tomcat6 restart

#####5) Add client public key for user ubuntu (see section 3) to ~/.ssh/authorized_keys

## 2. Setup Team Server

#####1) Create a standard EC2 ubuntu instance (e.g. m3 large).
Follow the instructions under the EC2 dashboard(in your AWS console) to create this ubuntu instance. Select m3 large from under the "General Purpose" tab of the Choose an Instance Type page.

** Don't forget to add your key pair! **

#####2) SSH to instance, update apt-get index and install required packages:

    sudo apt-get update
    sudo apt-get install default-jre
    sudo apt-get install sysstat

#####3) In your TS image's home folder create file unattended-setup.properties with the following content:

    userid=adminuser@aerofs.com
    password=adminuser_password

#####4) Download Team Server Installation package and unpack it:

	wget https://EC2_APPLIANCE_PUBLIC_DNS/static/installers/aerofsts-installer.tgz --no-check-certificate
    tar -xvf aerofsts-installer.tgz

#####5) Copy tools/sctest/teamServer/runTsTests.sh script into home folder (user ubuntu) in your EC2 team server instance

## 3. Setup Client image

#####1) Create EC2 standard ubuntu instance (t1.micro should be sufficient)

#####2) SSH to your instance, update apt-get index and install required packages:

    sudo apt-get update
    sudo apt-get install default-jre

#####3) Copy the following 2 scripts to your client's home folder (and ensure they have execute permissions):

    tools/sctest/client/signup.sh
    tools/sctest/client/startup.sh

#####4) Configure instance to run scripts on startup:

    sudo vi /etc/rc.local

    cd /home/ubuntu && su - ubuntu ./startup.sh

#####5) Generate ssh keys:
    ssh-keygen -t rsa (name - default; no passphrase)
    copy your public key (without host part)
    cat id_rsa.pub | sed s/@[^@]*$//

##### 6) Add public key from step 5) or the existing one above (if you use ami-b33f3ada) to your appliance's authorized_keys - section 1. step 5)

#####7) ONLY FOR NEW IMAGES: create EC2 image based on configured instance (use EC2 web console instances view)
* Right click on running instance -> "Create Image"

# 4. Run Tests

#####Note: You are bound to run out of memory eventually(~800 clients)in the TS while running these tests. Please refer to this on how to increase the storage space of the volume of your instance:
	http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ebs-expand-volume.html


#####1) Ensure you hava EC2 command line tools installed and configured.

http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/SettingUp_CommandLine.html

#####2) Ensure your Appliance and Team Server instances are up and running

#####3) Modify startClients.sh and set the following parameters:

    instancePrefix="YOURNAME-test-client" # unique prefix for client instancess

    applianceHost="EC2_APPLIANCE_PUBLIC_DNS" # AeroFS appliance EC2 public DNS name

    fileSizeKb=1024 # file size for each randomly generated file in kilobytes

    subnet=subnet-3927244d # subnet id for all your clients (see section 0.)

    image=ami-b33f3ada # client image id (section 3.)

    instanceType=t1.micro # EC2 instance type

    userEmailName="YOUR_USERNAME+sctest" # common prefix for all generated users

    userEmailDomain="aerofs.com" # common domain for all generated users

#####4) Modify terminateClients.sh - set the following parameter:

    instancePrefix="YOURNAME-test-client" # unique prefix for client instances.

 It should match instancePrefix in startClients.sh

#####5) Start Clients - run:

    ./tools/sctest/startClients.sh instancesPerUser, fileCount, userCount, [firstUserIndex]

    instancesPerUser - how many instances will be created for each user.

    fileCount - how many random files will be generated per each instance.

    userCount - how many users will be generated. Each user will have email: userEmailName[int1..userCount]@userEmailDomain

    [firstUserIndex] - optional parameter that specifies index for first generated user. If not specified 1 will be used

    firstUserIndex is useful when you run incremental tests. e.g. suppose you already have 30 (user index = 1..30) clients running, then the following command:
    	./startClients.sh 1 10 20 31
    will create 20 more instances starting from index 31, 10 files per user, 1 instance per user. So after this command succeeds number of instances is going to be 50 and user index = 1..50

    If startClients script succeeds you will see SUCCESS message at the end

#####6) Run Team Server tests: ssh to your EC2 Team Server instance and run:

    ./runTsTests.sh instancesPerUser, fileCount, userCount

    This will install team server using ts installation created at Section 2, step 4 and monitor TS data directory until it contains expected number of files

	When the tests finish it will create detailed report in testResults/tsTest_Iinst_Ffiles_Uusers/sctest_K.log where:
        I - instancesPerUser
        F - fileCount
        U - userCount
        K - ordinal of particular test,

	The tests are numbered according to the times you run them(1,2….) so results are never overwritten

    Each log contains total time test ran, number of files it synced and system statistics gathered in interval of 1 second:
    cpu stats, io stats, memory stats, network stats

#####7) Cleanup. After you ran your tests and collected results you will need to stop all clients. In order to do it run:
    ./terminateClients.sh

** NOTE: Appliance and Team Server instances should be started and stopped manually



# Setup And Running Shared Folders Scalability tests in EC2

Mechanics of shared folder testing is very similar. The only difference being that additional steps are executed to create and populate shared folders.

Script creates shared folders for each users in such way that each user shares excatly one folder with every other user. If N - number of users, number of shared folders is gonna be:
N*(N-1)/2
Here are changes that need to be done in process above to run shared folders tests (all other steps are unchanged):

# 2. Setup Team Server

#####...5) copy teamServer/sharedFoldersTsTests.sh (instead of runTsTests.sh) script into home folder (user ubuntu) in your EC2 team server instance
...

# 3. Setup Client image

#####…3) copy the following 2 scripts to home folder of your client image prototype (with execute permissions):
    client/signup.sh
    client/startupSharedFolders.sh (instead of startup.sh)

#####4) configure instance to run scripts on startup:
    sudo vi /etc/rc.local -> Add:
    cd /home/ubuntu && su - ubuntu ./startupSharedFolders.sh (instead of startup.sh)
...


# 4. Run Tests

#####…3) modify sharedFoldersStartClients.sh and set the following parameters:
    instancePrefix="YOURNAME-test-client" # unique prefix for client instancess
    applianceHost="EC2_APPLIANCE_PUBLIC_DNS" # AeroFS appliance EC2 public DNS name
    fileSizeKb=100 # file size for each randomly generated file in kilobytes
    subnet=subnet-3927244d # subnet id for all your clients (see section 0.)
    image=ami-03919c6a # client image id (section 3.)
    instanceType=t1.micro # EC2 instance type
    userEmailName="YOUR_USERNAME+sctest" # common prefix for all generated users
    userEmailDomain="aerofs.com" # common domain for all generated users

#####4) modify terminateClients.sh - set the following parameter:
    instancePrefix="YOURNAME-test-client" # unique prefix for client instancess
    it should match instancePrefix in sharedFoldersStartClients.sh

#####5) Start Clients - run:
    ./sharedFoldersStartClients.sh fileCount, userCount
    where
        instancesPerUser - how many instances will be created for each user
        fileCount - how many random files will be generated per each instance
        userCount - how many users will be generated. Each user will have email: userEmailName[int 1..userCount]@userEmailDomain
    E.g.
        ./sharedFoldersStartClients.sh 10 20 will create 20 instances, 20*(20-1)/2=190 distinct shares total, 10 files per shared folder; 190*10=1900 total files.
    If sharedFoldersStartClients script succeeds you will see SUCCESS message at the end
...

After sharedFoldersStartClients script finishes you will have to wait some time until it creates all shared folders and accepts all invitations (current delay before accepting invitations is 2 minutes)
It is recommended that you check in appliance web UI that number of shared folder is expected and all shared folders have 2 users (sometimes it misses some invitations, I don't know why)

If some folders are missing some users you can do one of the following:

1. login as missing user and accept the rest of invitations, OR
2. simply restart corresponding client instances - it will reaccept missing invitations on startup

5) Run Team Server tests: ssh to your EC2 Team Server instance and run:

    ./sharedFoldersTsTests.sh fileCount, userCount
    This will install team server using ts installtion created at Section 2, step 4 and monitor TS data directory until it contains expected number of files.

    When test finishes it will create detailed report in testResults/sharedFoldersTsTest_Iinst_Ffiles_Uusers/sctest_K.log where:
        I - instancesPerUser - always 1 in case of shared folder tests
        F - fileCount
        U - userCount
        K - ordinal of particular test, so if you ran this test once it is going to be 1, second time - 2, etc, so results are never overwritten

    Each log contains total time test ran, number of files it synced and system statistics gathered in interval of 1 second:
    cpu stats, io stats, memory stats, network stats

6) Cleanup. After you ran your tests and collected results you will need to stop all clients. In order to do it run:

    ./terminateClients.sh  (same script as for user testing)


# Other notes
1) Script client/rmAll.sh can be used for troubleshooting on client side - it stops aerofs processes if running, removes all aerofs deployment and data folders and logs
2) If something is messed up in appliance data you can reset the db (I am not sure if there is a good way to reset appliance db) or reinstall appliance
