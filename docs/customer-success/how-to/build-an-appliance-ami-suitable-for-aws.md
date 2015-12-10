# How-to Build an Appliance Suitable for AWS

Deploying updated appliances to AWS is the worst. It's a manual process where
we create an AMI and we _email_ a _spreadsheet_ to an account manager, who
involves a different team to do a security check, who then manually approves
our AMI version in their app store. It's error prone and slow.

So, let's build an appliance that pulls the latest docker images on first boot,
so we don't have to do an AWS build that often. (In fact, with the
aforementioned magical appliance, we'll only need to update the AMI if our
cloud-config file changes, or if we need to roll the CoreOS version.)

N.B. the appliance created by the process below does not include the terminal
controller piece. Therefore, it is not suitable for VBox and VMWare deployment.
It is best suited for cloud services like AWS and Rackspace. At some point in
the future, we might consider auto-magically downloading the latest container
images on first boot for on-prem deployments as well. But for now, we will do
this only for AWS. Some on-prem folks will want their appliances to be
air-gapped anyhow, so phoning home is a no-no.

## Building

1. Launch the latest CoreOS stable in ec2.

   Properties:

        Size: m3.medium
        Image: Latest CoreOS Stable HVM.
        Network: production
        VPC: public-production
        Auto-Assign IP: enable

   Use a 50 GB disk and use the security group `aerofs.appliance`.

2. `ssh` to your new CoreOS box and run the following. The last command might
   take a few minutes to complete.

   Commands:

        ssh -i <your_pem_file> core@<private_ip>
        sudo su
        rm -rf /home/core/.ssh/authorized_keys*
        wget https://raw.githubusercontent.com/aerofs/aerofs-docker/master/cloud-config.yml
        mkdir -p /var/lib/coreos-install/
        mv cloud-config.yml /var/lib/coreos-install/user_data && shutdown now

3. Make sure the instance is stopped and create an AMI using the Create Image command on the
   EC2 interface. Name the instance "AeroFS Appliance". Set the image description to "AeroFS
   Appliance MM/DD/YYYY".

4. Give Yuri the AMI ID and he'll get it into the Amazon app store.
