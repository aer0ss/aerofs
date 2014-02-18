How to Setup and Run Scalability Tests in Amazon ec2

=== 0. Prepare EC2 VPC and subnet ===
You can use either existing test vpc (test-vpc: vpc-54fb1231) and subnet: subnet-3927244d (default security group is open to the world) or create your own
Make sure when you launch your appliance, team server and client instances to use the same subnet

=== 1. Setup Appliance ===
1) Create appliance ec2 image following these steps: https://support.aerofs.com/entries/26208024-Launching-your-AeroFS-Appliance-in-Amazon-EC2
2) start ec2 instance based on appliance image and ensure you can ssh to it. 
ssh -i ~/.ssh/YOUR_EC2_PRIVATE_KEY.pem ubuntu@EC2_APPLIANCE_PUBLIC_DNS
I recommend setup all instances in VPC and assign elastic IP to your appliance instance so dns name is permanent 
Make sure all necessary ports are open (for testing purposess you can use security group "open" so everything is open to the world)
3) Setup appliance following regular setup steps in browser and create your first user, e.g. ADMINUSER@aerofs.com 
4) Enable open signup and restart tomcat:
sudo vi /opt/config/templates/server.tmplt => set open_signup=true
sudo service tomcat6 restart

5) add client public key for user ubuntu (see section 3) to ~/.ssh/authorized_keys  

=== 2. Setup Team Server ===
1) Create standard ec2 ubuntu instance (e.g. m3.large)
I recommend setup all instances in VPC and assign elastic IP to your team server instance so dns name is permanent
2) ssh to instance, update apt-get index and install equired packages:
sudo apt-get update
sudo apt-get install default-jre
sudo apt-get install sysstat
3) in home folder create file unattended-setup.properties with the following content:
userid=ADMINUSER@aerofs.com
password=ADMINUSER_PASSWORD
4) Download Team Server Installation package and unpack it:
wget https://EC2_APPLIANCE_PUBLIC_DNS/static/installers/aerofsts-installer.tgz --no-check-certificate 
tar -xvf aerofsts-installer.tgz
5) copy teamServer/runTsTests.sh script into home folder (user ubuntu) in your ec2 team server instance

=== 3. Setup Client image ===
You can use existing image (sergey-sctest-client-img ami-b33f3ada) and skip next section (goto step 6) or you can create new image following steps below
= Creating image =
1) Create ec2 standard ubuntu instance (t1.micro should be sufficient)
2) ssh to your instance, update apt-get index and install required packages:
sudo apt-get update
sudo apt-get install default-jre
3) copy the following 2 scripts to home folder of your client image prototype (and ensure they have execute permissions):
client/signup.sh
client/startup.sh
4) configure instance to run scripts on startup:
sudo vi /etc/rc.local -> Add:
cd /home/ubuntu && su - ubuntu ./startup.sh
5) generate ssh keys:
ssh-keygen -t rsa (name - default; no passphrase)
copy your public key (without host part)
cat id_rsa.pub | sed s/@[^@]*$//
===
Here is public key for existing image sergey-sctest-client-img ami-b33f3ada
ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDO8zv3TxpJbdoirYsxv30U6aXBtQ6v42QyBoaCyO+4Nsq3ZsT4GnHrZuJxvV5xT2bK3bLUlDV8vo4p3vh0DmXm9SOo2B7Oa8DJkqLVYtvpbEKKdenbq8qjH24bknqSQUFn+ohFVze4cYUUjyjgjfEZd0TsLfztEAu4hGIIFaPRD2Sy+5jVgUeHwh31t/KB+wF7NTRjVwrp2p2UwxcKqtdzRbx/KhHHBJPuv7Vp9V29yeldWj1DUvgsSRnsCNySNOzdS9WZmr6+R9xgxugRKht00eVecZWU1zGge1tp1UJoD+KlBhe0h2m1F4PhBtAO9+ZkoOl9p1t6IaAzl70KHI/N ubuntu

6) add public key from step 4) or existing one above(if you use ami-b33f3ada) to your appliance authorized_keys - section 1. step 5) 
7) ONLY FOR NEW IMAGES: create ec2 image based on configured instance (use ec2 web console -> instances view -> right click on running instance -> "Create Image") and remember image id, let's call it CLIENT_IMAGE_ID

=== 4. Run Tests ===
1) ensure you hava ec2 command line tools installed and configured: http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/SettingUp_CommandLine.html
2) ensure your Appliance and Team Server instances are up and running
3-a) modify startClients.sh and set the following parameters:
instancePrefix="YOURNAME-test-client" # unique prefix for client instancess
applianceHost="EC2_APPLIANCE_PUBLIC_DNS" # AeroFS appliance ec2 public dns name 
fileSizeKb=1024 # file size for each randomly generated file in kilobytes
subnet=subnet-3927244d # subnet id for all your clients (see section 0.)
image=ami-b33f3ada # client image id (section 3.)
instanceType=t1.micro # ec2 instance type
userEmailName="YOUR_USERNAME+sctest" # common prefix for all generated users
userEmailDomain="aerofs.com" # common domain for all generated users
3-b) modify terminateClients.sh - set the following parameter:
instancePrefix="YOURNAME-test-client" # unique prefix for client instancess
it should match instancePrefix in startClients.sh


4) Start Clients - run: 
./startClients.sh instancesPerUser, fileCount, userCount, [firstUserIndex]
where
instancesPerUser - how many instances will be created for each user
fileCount - how many random files will be generated per each instance
userCount - how many users will be generated. Each user will have email: userEmailName[int 1..userCount]@userEmailDomain
[firstUserIndex] - optional parameter that specifies index for first generated user. If not specified 1 will be used
firstUserIndex is useful when you run incremental tests. e.g. suppose you already have 30 (user index = 1..30) clients running. The following command:
./startClients.sh 1 10 20 31 will create 20 more instances starting from index 31, 10 files per user, 1 instance per user. So after this command succeeds number of instances is going to be 50 and user index = 1..50
If startClients script succeeds you will see SUCCESS message at the end

5) Run Team Server tests: ssh to your ec2 Team Server instance and run:
./runTsTests.sh instancesPerUser, fileCount, userCount
it will install team server using ts installtion created at Section 2, step 4 and monitor TS data directory until it contains expected number of files
when test finishes it will create detailed report in testResults/tsTest_Iinst_Ffiles_Uusers/sctest_K.log where:
I - instancesPerUser
F - fileCount
U - userCount
K - ordinal of particular test, so if you ran this test once it is going to be 1, second time - 2, etc, so results are never overwritten 
Each log contains total time test ran, number of files it synced and system statistics gathered in interval of 1 second:
cpu stats, io stats, memory stats, network stats

6) Cleanup. After you ran your tests and collected results you will need to stop all clients. In order to do it run:
./terminateClients.sh

NOTE: Appliance and Team Server instances should be started and stopped manually 