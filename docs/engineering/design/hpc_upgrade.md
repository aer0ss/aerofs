# Upgrading And Scaling Hosted Private Cloud

## Requirements

    - Upgrade all deployment(s) in all HPC server(s).
        - Automated, efficient, minimal user interaction.
    - Scale up HPC servers cluster
        - Automatically spin up new servers when all existing servers in HPC cluster have
          crossed deployment threshold limit.
    - Scale down HPC servers cluster
        - Automatically take down an HPC server if it has no active deployments on it.
    - Prioritize using reserved instances when scaling up and removing on-demand instances
      when scaling down HPC server cluster.

## Upgrading HPC servers

#### Background

Each HPC server has multiple AeroFS deployments. Each server has a single copy of each AeroFS
and HPC specific image. Each server then hosts per deployment containers which are instances of
the images. So, while HPC specific images will have one container per image per server, we can have
multiple containers per AeroFS application image per server(depending upon the number of
deployments each server hosts).

#### What does it mean to upgrade?

    - All HPC images are upgraded in an HPC server. New HPC containers based of the upgraded
      images are brought up and old containers/images are stopped and removed.
    - All AeroFS application images are upgraded in an HPC server. New AeroFS containers are
      brought up based on upgraded images and old containers/images are stopped and removed.

The above two steps must happen for all servers in the HPC clusters.

#### How?

As with any AeroFS deployment, there are two options that we have to upgrade HPC servers:

    - Inplace Upgrade: Each server is upgraded in place i.e. we download latest images(HPC and AeroFS
      application), shutdown all existing containers, bring up new containers based on the latest
      images and remove the old continers.

    - The other option is to run a script that automates our manual upgrade process i.e. we first
      back up each and every deployment on a server we want to upgrade, spin up a new server which
      by default will be configured with all the latest images and bring up new containers for
      each deployment that we backed up and bring restore the deployments using the downloaded
      backup file.

We propose choosing option 1 for the following reasons:

    - IPU is simpler.
    - Weekly updates with option 2 will require launching new servers every single time.
    - Less down time for deployments.

Note that upgrade flow/timing will be completely owned by AeroFS i.e. individual deployment
owners will not have the option to upgrade their deployments. We will perform upgrades for the
whole cluster periodically(preferrably weekly).

#### Detailed design

    1. We will expose a new POST route in lizard /hpc_server/upgrade which will initiate the upgrade
    task.
    Anyone with access to enterprise.aerofs.com:8000/hpc_servers can start the upgrade task.
    2. We will launch a new celery worker that will download the new images from repository.
    3. On completion of step 2 we will launch a new celery task per server that will be responsible
    for upgrading its assigned server. This will help parallelize the upgrade process.
    4. Each celery task will then:
        1. Send start maintenance notification to all deployment owners in that particular server.
           Also, bring down/switch off all Amazon CloudWatch notifications.
        2. Shutdown all AeroFS containers for all deployments
        3. Shutdown all HPC containers for all deployments
        4. Start new HPC containers.
        5. Start new AeroFS application containers for each deployment.
        6. Run repackaging for every single deployment in the server.
        7. Send end maintenance notification to all deployment owners in that particular server.
           Bring back up all Amazon CloudWatch notification.
        8. Clean all unused images/containers.


## Scaling HPC cluster

#### Reserved instances

https://aws.amazon.com/ec2/purchasing-options/reserved-instances/

N.B. Based on our historical data and estimates for new signups, it is more cost efficient
to have x reserved instances where x is avg number of new AeroFS signups(subject to change).
Any scaling up or down will be performed on the extra on-demand instances launched to accomodate
the deployments.

#### Scaling up

    - When all reserved instances can no longer host any new deployments(limited factor: memory
      usage), then we start launching new on demand instance(s) to host new deployments.
    - We always prefer using reserved instances to launch new deployments. This will limit the need
      for new on demand instances.

#### Scaling down

    - Scaling down i.e. removing unnecessary on demand instances will be performed by recognizing
      expired/inactive deployments and removing them. Any instance with no active deployments
      will be terminated.

#### Expired Deployments

    - Any deployment that has an expired license will be considered an expired deployment. We will run
      a celery/cron task that periodically watches for expired deployments and removes them.
    - Before we remove a deployment, we will store a backup the deployment data in S3. This is done
      so that a customer who forgets to extend license/wishes to try AeroFS longer doesn't have
      to start from scratch.
    - Deployment owners are sent regular notifications:
        - Notify a week before license is expiring
        - Notify a day before the license is expiring
        - As license expires, instructions to re-setup their deployment


#### Inactive deployments
    - Any deployment that has no daily active users for a week is considered inactive.
    - Any inactive deployment is put into maintenance mode for a week and if not reclaimed by their
      owner within a week is then terminated.
    - Owners of inactive deployments are sent periodic notifications:
        - Warning them that their deployment is about to be put in maintenance mode and
          instructions on how to avoid it.(~24hrs before putting their deployment in maintenance).
        - When their deployments are put in maintenance mode and how to reclaim it.
          To reclaim a deployment means to bring it out of maintenance mode.
        - When their deployments are terminated and how to re-setup it.

To re-setup an expired deployment, use a valid license file and backup file(via S3) and restore
from backup.
