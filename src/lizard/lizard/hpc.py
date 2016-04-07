import boto3
import botocore.exceptions
import datetime
import os
import re
import requests
import subprocess
import time

from celery import chord, group
from docker import Client, tls
from flask import current_app
from hpc_config import configure_deployment, get_boot_id, new_authed_session, reboot, repackage
from lizard import celery, db, notifications
from models import HPCDeployment, HPCServer, Admin
from sqlalchemy.exc import IntegrityError

EXPIRING_LICENSE_NOTIFICATION_BUFFER = 7
MONITORING_PORT = 5000
POLLING_INTERVAL = 2

# This variable is only for testing purpose
NUM_RESERVED_INSTANCES = 2
MEM_USAGE_DEPLOYMENT_THRESHOLD = 80


HPC_SECRETS_PATH = '/opt/lizard/hpc-server-config/secrets'

BACKUP_BUCKET = 'aerofs.hpc.backupfiles'
HPC_SQS_QUEUE_NAME = 'hpc_auto_scaling'
HPC_AUTOSCALING_GROUP_NAME = 'hpc_autoscaling_group'

dirname = os.path.abspath(os.path.dirname(__file__))

class DeploymentAlreadyExists(Exception):
    def __init__(self):
        self.msg = "Sorry, that name is already taken. Please try another name."
        Exception.__init__(self)

    def __str__(self):
        return repr(self.msg)


class NoServerAvailable(Exception):
    def __init__(self):
        Exception.__init__(self, "There is no available server to create a deployment on.")


def get_docker_client(deployment):
    """
    Returns a docker client that will communicate with the Docker daemon at the given deployment's server
    """
    tls_config = tls.TLSConfig(
        client_cert=(current_app.config['HPC_PATH_TO_DOCKER_CLIENT_CERT'],
                     current_app.config['HPC_PATH_TO_DOCKER_CLIENT_KEY']),
        verify=current_app.config['HPC_PATH_TO_DOCKER_CA_CERT'],
        assert_hostname=False)

    return Client(base_url=deployment.server.docker_url, tls=tls_config, version='auto', timeout=5)


# If restore is True, we have to upgrade the deployment.
def create_deployment(customer, subdomain, delay=0, restore=False, server_id=None):
    """
    Create a new deployment for a given customer at a given subdomain.
    """

    if not restore:
        server = pick_server()

        # Adding the deployment to the db
        deployment = HPCDeployment()
        deployment.customer = customer
        deployment.subdomain = subdomain
        deployment.server = server
        deployment.set_days_until_expiry(30)
        deployment.setup_status = HPCDeployment.status.IN_PROGRESS
        db.session.add(deployment)
    else:
        # If we are restoring the deployment we just change the server on which
        # the deployment is.
        deployment = HPCDeployment.query.get(subdomain)
        if server_id:
            deployment.server = HPCServer.query.get(server_id)
        else:
            server = pick_server()
            deployment.server = server

    # Commit the changes to DB.
    # If the subdomain already exists, try to throw a more useful exception type than IntegrityError
    try:
        db.session.commit()
    except IntegrityError as ex:
        db.session.rollback()
        # Parse the error message... yes it's dirty but it's the only way
        raise DeploymentAlreadyExists() if re.match('UNIQUE.*subdomain', ex.orig.message) else ex

    # Create the subdomain on Route 53
    create_subdomain(deployment)

    # Start a chain of celery tasks to configure the deployment
    (
        sail.si(subdomain) |
        configure_deployment.si(subdomain, restore) |
        reboot.si(subdomain) |
        repackage.si(subdomain)

    ).apply_async(link_error=save_error.si(subdomain), countdown=delay)


@celery.task()
def save_error(subdomain):
    deployment = HPCDeployment.query.get(subdomain)
    deployment.setup_status = HPCDeployment.status.DOWN
    db.session.commit()


# First we begin by upgrading the x most used instances where x is the number
# of reserved instances
@celery.task()
def upgrade_all_instances():
    sorted_instances = _sort_server_by_num_deployments()
    upgrade_single_instance_tasks = []
    most_used_instances = sorted_instances[:NUM_RESERVED_INSTANCES]
    least_used_instances = sorted_instances[NUM_RESERVED_INSTANCES:]

    while len(most_used_instances) > 0:
        # This will be done NUM_RESERVED_INSTANCE times
        instance = most_used_instances.pop(0)
        upgrade_single_instance_tasks.append(
            upgrade_single_instance.si(instance.id))

    while len(least_used_instances) > 0:
        # We only pull new images on these instances
        instance = least_used_instances.pop(0)
        upgrade_single_instance_tasks.append(
            pull_server_images.si(_internal_ip(instance.docker_url), wait=True))

    # `chord` is celery function whose first parameter is a list of celery
    # tasks to execute and second parameter is the function to call when every
    # task of the list has finished. Here, when all the RI are upgraded, we
    # call the function `upgrade_remaining_deployments()`
    chord(upgrade_single_instance_tasks)(upgrade_remaining_deployments.si(
        least_used_instances))


@celery.task()
def upgrade_remaining_deployments(sorted_instance_list):
    # Getting the id of all the instances that are On Demand Instances (the least
    # crowded instances)
    server_ids_odi = [instance.id for instance in sorted_instance_list]

    remaining_deployments_to_upgrade = HPCDeployment.query.filter(
        HPCDeployment.server_id.in_(server_ids_odi)).all()

    # Upgrading the remaining deployments. This deployments should be stored
    # either on reserved_instance or ODI. This will be decided by `pick_server()`
    for deployment in remaining_deployments_to_upgrade:
        upgrade_deployment(deployment)

    # TODO(DS): 1. Remove old images
    current_app.logger.info("Upgrade finished")

    # Removing empty servers
    for server_id in server_ids_odi:
        delete_server_if_empty(server_id)


@celery.task()
def upgrade_single_instance(server_id):
    server = HPCServer.query.get(server_id)

    # 1. Call docker pull latest images in each server.
    pull_server_images(_internal_ip(server.docker_url), wait=True)

    # 2. After Step 1 finishes put each deployment in maintenance mode.
    # and get their backups. Upload the backups to s3 and restore the deployments
    # from backup
    for deployment in server.deployments:
        upgrade_deployment(deployment, server_id)


def upgrade_deployment(deployment, server_id=None):
        current_app.logger.info('Upgrade: Backing up {}'.format(deployment.subdomain))
        download_backup_file(deployment.subdomain)
        current_app.logger.info('Upgrade: Deleting {}'.format(deployment.subdomain))
        deleted = delete_deployment(deployment, upgrade=True)
        if deleted:
            current_app.logger.info('Upgrade: Deployment {} was deleted. Restoring \
                                    it with latest version images.'.format(deployment.subdomain))
            create_deployment(deployment.customer,
                              deployment.subdomain,
                              restore=True,
                              server_id=server_id)
        else:
            current_app.logger.warning('Deployment {} was NOT deleted'.format(
                deployment.subdomain))


def create_route53_change(deployment, delete=False):
    return {
        'Changes': [
            {
                'Action': 'CREATE' if not delete else 'DELETE',
                'ResourceRecordSet': {
                    'Name': deployment.full_hostname(),
                    'Type': 'A',
                    'TTL': 300,
                    'ResourceRecords': [{'Value': deployment.server.public_ip}]
                }
            }
        ]
    }


def create_subdomain(deployment):
    """
    Creates the subdomain on Amazon's Route 53
    In development mode, the subdomain will be created on syncfs.com
    """
    change = create_route53_change(deployment)
    response = current_app.route53.change_resource_record_sets(
        HostedZoneId=current_app.config['HPC_ROUTE_53_HOSTED_ZONE_ID'], ChangeBatch=change)

    current_app.logger.info("Created subdomain %s", response)


def delete_subdomain(deployment):
    """
    Deletes the subdomain on Amazon's Route 53
    """
    change = create_route53_change(deployment, delete=True)
    try:
        response = current_app.route53.change_resource_record_sets(
            HostedZoneId=current_app.config['HPC_ROUTE_53_HOSTED_ZONE_ID'], ChangeBatch=change)
        current_app.logger.info("Deleted subdomain %s", response)
    except botocore.exceptions.ClientError as ex:
        current_app.logger.warn("Deleting subdomain failed - ignoring. %s", ex)


@celery.task()
def sail(subdomain):
    """
    Runs the hpc-sail container which will take care of configuring and starting the loader
    """
    deployment = HPCDeployment.query.get(subdomain)
    if deployment is None:
        raise Exception("Deployment '{}' not found in the database".format(subdomain))

    current_app.logger.info("Running hpc-sail for subdomain %s on host %s", deployment.subdomain, deployment.server.docker_url)

    docker = get_docker_client(deployment)

    # Create the container
    container = docker.create_container(
        image='registry.aerofs.com/aerofs/hpc-sail',
        volumes=['/var/run/docker.sock', '/hpc/deployments/'],
        host_config=docker.create_host_config(
            binds=['/var/run/docker.sock:/var/run/docker.sock', '/hpc/deployments/:/hpc/deployments/'],
        ),
        command=[deployment.subdomain]
    )

    warnings = container.get('Warnings')
    if warnings:
        current_app.logger.warn("Docker warnings: %s", warnings)

    # Start the container
    container_id = container.get('Id')
    docker.start(container=container_id)

    # Wait up to 10 minutes for hpc-sail to finish.
    try:
        exit_code = docker.wait(container_id, 600)
    except requests.exceptions.ReadTimeout:
        current_app.logger.warn("hpc-sail timed out for subdomain {}".format(subdomain))
        exit_code = -2

    # Show the container logs in case of failure
    if exit_code != 0:
        logs = docker.logs(container_id, tail=100)
        current_app.logger.warn("""hpc-sail failed for subdomain {}. Exit code {}. Logs:
        ##################################
        {}
        ##################################""".format(subdomain, exit_code, logs))

    # Delete the hpc-sail container
    docker.remove_container(container_id)


def delete_containers(deployment):
    """
    Deletes all containers whose name start with the deployment's subdomain
    """

    # Sanity check to make sure we don't have malformed / empty / None subdomains
    assert len(deployment.subdomain) >= 1

    docker = get_docker_client(deployment)

    # Find all containers whose name start with the subdomain
    containers = [c for c in docker.containers(
        all=True,  # include non-running containers
        filters={'name': '^/{}-hpc-*'.format(deployment.subdomain)})]

    # Sanity check: make sure that we didn't get an unreasonably large number of containers. This number is arbitrary.
    # If a typical AeroFS appliance grows to more than 40 containers, just update the number here accordingly.
    assert len(containers) < 40

    # Remove the containers
    for container in containers:
        current_app.logger.info("Deleting container %s", container)
        docker.remove_container(container['Id'], force=True)


# If upgrade is set to True, we remove the containers but do not remove the
# deployment from the db but just set the server ID to null.
@celery.task()
def delete_deployment(deployment, upgrade=False):
    current_app.logger.info("Deleting deployment %s", deployment.subdomain)
    try:
        # This may fail if the server is unreachable
        # Just ignore any errors for now. In the future we might want to ask the user.
        delete_containers(deployment)
    except Exception as ex:
        current_app.logger.warn("Deleting deployment: Ignoring exception %s \
                while deleting containers. ", ex)
        return False

    delete_subdomain(deployment)
    hosting_server_id = deployment.server_id

    if not upgrade:
        db.session.delete(deployment)
    else:
        deployment.server_id = None
    db.session.commit()

    delete_server_if_empty(hosting_server_id)

    return True


# This function returns the list of instances sorted by the number of deployments
# they host.
def _sort_server_by_num_deployments():
    count_deployments = db.func.count(HPCDeployment.subdomain)
    server = db.session.query(HPCServer) \
        .outerjoin(HPCDeployment) \
        .group_by(HPCServer.id) \
        .order_by(count_deployments.desc()) \
        .all()

    # Throws an exception if there are no servers available
    if not server:
        raise NoServerAvailable()

    return server


def pick_server():
    """
    Returns the "best" server  to create a new deployment on.
    We always want to pick up the most crowded instance provided that it has
    enough memory to host a new deployment
    """
    sorted_instances = _sort_server_by_num_deployments()

    for instance in sorted_instances:
        instance_stats = get_server_sys_stats(instance.docker_url)
        if (instance_stats is not None and
           instance_stats['mem_usage_percent'] < MEM_USAGE_DEPLOYMENT_THRESHOLD):
            return instance

    # If we did not exit from the previous loop, it means that there are no
    # server available to create a deployment on.
    raise NoServerAvailable()


def delete_server_from_db(server):
    current_app.logger.info("Deleting server %s", server.docker_url)

    if len(server.deployments) > 0:
        raise Exception("Server {} is not empty".format(server.id))

    db.session.delete(server)
    db.session.commit()


# Storing the backup file of the deployment being deleted on a Amazon S3 instance
def add_backup_s3(file_name, file_path):
    backup_bucket = current_app.s3.Bucket(BACKUP_BUCKET)
    backup_bucket.upload_file(file_path, file_name)


@celery.task()
def check_expired_deployments():
    deployment_list = HPCDeployment.query.all()

    # We are checking the expiry date of each deployment
    for deployment in deployment_list:
        days_until_expiry = deployment.get_days_until_expiry()
        if days_until_expiry == EXPIRING_LICENSE_NOTIFICATION_BUFFER:
            admins = Admin.query.filter(
                Admin.customer_id == deployment.customer.id).all()
            for admin in admins:
                notifications.send_hpc_nearly_expired_license_email(
                    admin, EXPIRING_LICENSE_NOTIFICATION_BUFFER)
        if days_until_expiry == 0:
            admins = Admin.query.filter(
                Admin.customer_id == deployment.customer.id).all()
            for admin in admins:
                notifications.send_hpc_expired_license_email(admin)

            download_backup_file(deployment)
            backup = current_app.s3.Object(BACKUP_BUCKET, set_backup_name(deployment.subdomain))
            try:
                # content_length attributes throws an exception if the backup file
                # does not exist.
                backup.content_length
                # Deleting the deployment
                delete_deployment(deployment)
            except botocore.exceptions.ClientError:
                current_app.logger.info("Failed to remove deployment {}".format(
                    deployment.subdomain))


@celery.task()
def download_backup_file(subdomain):
    session = new_authed_session(subdomain)

    # Toggling maintenance mode
    old_id = get_boot_id(session)
    session.post("/admin/json-boot/maintenance")

    # Wait for new boot id, or until we time out
    while True:
        new_id = get_boot_id(session)
        current_app.logger.debug("New id {}".format(new_id))
        if new_id is not None and new_id != old_id:
            break

        time.sleep(POLLING_INTERVAL)

    # Generate the backup file
    session.post('/admin/json-backup')

    # Check when the backup file is ready
    while True:
        backup_request = session.get('/admin/json-backup')
        if backup_request.status_code == 200:
            status = backup_request.json()
            current_app.logger.info(status)
            if status['succeeded']:
                break
        else:
            current_app.logger.warn('Failed to delete deployment {}'.format(subdomain))
            return

    #Download the backup file into tmp dir and storing it in a S3 bucket
    backup_file = session.get('/admin/download_backup_file')
    backup_name = set_backup_name(subdomain)
    backup_path = os.path.join("/tmp", backup_name)
    with open(backup_path, 'wb') as f:
        for chunk in backup_file.iter_content(chunk_size=1024):
            if chunk:
                f.write(chunk)
    add_backup_s3(backup_name, backup_path)
    os.remove(backup_path)


def set_backup_name(subdomain):
    backup_date = datetime.datetime.today().strftime("%Y%m%d")
    return "{}_backup_{}".format(subdomain, backup_date)


def create_server(instance_type, server_name):
    create_aws_credentials_file()
    cloud_config_path = os.path.join(dirname, 'cloud-config')

    with open(cloud_config_path) as f:
        user_data = f.read()

    instance = current_app.ec2_resource.create_instances(
        ImageId='ami-7f3a0b15',  # CoreOS stable 835.13.0 HVM
        MinCount=1,
        MaxCount=1,
        KeyName='hpc-key',
        UserData=user_data,
        InstanceType=instance_type,
        BlockDeviceMappings=[
            {
                'DeviceName': '/dev/xvda',
                'Ebs': {
                    'SnapshotId': 'snap-0b0dbb9d',
                    'VolumeSize': 300,
                    'DeleteOnTermination': True,
                    'VolumeType': 'gp2',
                },
            },
        ],
        NetworkInterfaces=[
            {
                'DeviceIndex': 0,
                'SubnetId': 'subnet-61f94b0c',  # public-production
                'Groups': ['sg-a76e09de'],  # aerofs.hosted_private_cloud
                'DeleteOnTermination': True,
                'AssociatePublicIpAddress': True
            },
        ],
    )

    instance = instance[0]
    instance.create_tags(Tags=[{'Key': 'Name', 'Value': server_name}])

    # Wait that the server has finished initializing
    instance.wait_until_running()

    while True:
        instance_status = current_app.ec2_resource.meta.client.describe_instance_status(
            InstanceIds=[instance.id])['InstanceStatuses'][0]
        if instance_status['InstanceStatus']['Status'] == 'ok':
            break
        current_app.logger.debug('Waiting for the Status Check')
        time.sleep(POLLING_INTERVAL)

    if instance.state.get('Name', None) != 'running':
        current_app.logger.info(
            "Error: The instance {} is in state: {}".format(instance.id, instance.state))
        return False

    return instance


# wait is set to true if pulling new images when upgrading, otherwise false.
@celery.task()
def pull_server_images(server_private_ip, wait=False):
    current_app.logger.info('Pulling images in server {}'.format(server_private_ip))
    config_script_path = os.path.join(dirname, 'configure_hpc_server.sh')

    if not wait:
        subprocess.Popen([config_script_path, server_private_ip])
    else:
        pulling = subprocess.Popen([config_script_path, server_private_ip])
        while pulling.poll() is None:
            time.sleep(POLLING_INTERVAL)


def configure_server(instance):
    # To configure the server we need both to launch the 'configure_hpc_server.sh'
    # script
    private_ip = instance.private_ip_address
    pull_server_images(private_ip)

    # Attach the instance to an an autoscaling group
    attach_instance_autoscaling_group(instance.id)

    # Add the instance to the DB
    server = HPCServer()
    server.docker_url = 'https://{}:2376'.format(private_ip)
    server.public_ip = instance.public_ip_address
    db.session.add(server)
    db.session.commit()


@celery.task()
def launch_server(server_name=''):
    if not server_name:
        last_server_id = HPCServer.query.order_by(-HPCServer.id).first().id
        server_name = 'hpc-server-{}'.format(last_server_id+1)

    # Lauching the server
    instance = create_server('r3.2xlarge', server_name)
    configure_server(instance)

    return instance.id


@celery.task()
def check_sqs_notifications():
    # Connecting to the queue
    queue = current_app.sqs_resource.get_queue_by_name(QueueName=HPC_SQS_QUEUE_NAME)

    # Get the number of message from the queue
    queue_attributes = current_app.sqs_client.get_queue_attributes(
        QueueUrl=queue.url,
        AttributeNames=['ApproximateNumberOfMessages'])
    number_messages_queue = int(queue_attributes['Attributes']['ApproximateNumberOfMessages'])

    # If there is a message in the queue, it means that the alarm notified us
    # that we have to create new a instance
    if number_messages_queue != 0:
        launch_server.si().apply_async()
        # By calling lizard, we took into account the notification so we can now
        # delete the messages from the queue.
        queue.purge()


def attach_instance_autoscaling_group(instance_id):
    current_app.autoscaling.attach_instances(
        InstanceIds=[instance_id],
        AutoScalingGroupName='hpc_test_group'
    )


def create_aws_credentials_file():
    # In the HPC Monitoring container, the memory monitoring script need credentials
    # to run
    credential_path = os.path.join(HPC_SECRETS_PATH, 'aws_credentials')
    if not os.path.isfile(credential_path):
        with open(credential_path, 'w') as cred:
            cred.write('AWSAccessKeyId={} \n'.format(current_app.config['HPC_AWS_ACCESS_KEY']))
            cred.write('AWSSecretKey={}'.format(current_app.config['HPC_AWS_SECRET_KEY']))


# This function gives the status of a subdomain
def subdomain_deployment_status(subdomain):
    # Getting the status of the each part of a deployment
    try:
        session = new_authed_session(subdomain)
        r = session.get('/admin/json-status')
        subdomain_status = r.json()
    except Exception as e:
        subdomain_status = {'statuses': [{'is_healthy': False,
                                          'message': str(e)}]}
    return subdomain_status


# This function returns a list that is empty if all deployments of the server
# are up. If not, the list contains the name of the deployments that are down.
def get_problematic_deployments(server_id):
    # we get the status only of the deployments that are in the server which id
    # is given as a parameter
    deployments_list = HPCDeployment.query.filter(
        HPCDeployment.server_id == server_id,
        HPCDeployment.setup_status != HPCDeployment.status.IN_PROGRESS).all()

    problematic_deployments = []

    for deployment in deployments_list:
        # We get the status of all the container of each deployment
        statuses = subdomain_deployment_status(deployment.subdomain)

        # If one of the container is down, we add it to the list of down deployments
        for status in statuses['statuses']:
            if not status['is_healthy']:
                problematic_deployments.append(deployment.subdomain)

    return problematic_deployments


def _internal_ip(docker_url):
    # Docker url is of the form https://<ip-addr>:port>.
    # Use rfind to get index of 2nd ":" in docker url and extract away port
    # to get internal ip.
    end = docker_url.rfind(":")
    return docker_url[len("https://"):end]


def get_server_sys_stats(docker_url):
    try:
        url = 'http://{}:{}'.format(_internal_ip(docker_url), MONITORING_PORT)
        r = requests.get(url)
        return r.json()
    except requests.ConnectionError:
        return None


# We delete the server if:
# 1. It is empty. 2. # of servers > NUM_RESERVED_INSTANCES because we don't want
# to have less than  NUM_RESERVED_INSTANCES servers.
def delete_server_if_empty(server_id):
    server = HPCServer.query.get(server_id)

    if (db.session.query(HPCServer).count() > NUM_RESERVED_INSTANCES and
       len(server.deployments) == 0):
        server_aws_id = get_aws_instance_id_from_public_ip(server.public_ip)
        current_app.logger.info('Removing instance {}'.format(server_aws_id))

        # Detach the instance from the autoscaling group and removing it
        current_app.autoscaling.detach_instances(
            InstanceIds=[server_aws_id],
            AutoScalingGroupName=HPC_AUTOSCALING_GROUP_NAME,
            ShouldDecrementDesiredCapacity=True)
        current_app.ec2_client.terminate_instances(InstanceIds=[server_aws_id])
        delete_server_from_db(server)
        current_app.logger.info('Instance {} removed.'.format(server_aws_id))


def get_aws_instance_id_from_public_ip(public_ip):

    # Retrieving the instances of the autoscaling group in order to compare
    # their public ip address and therefore get the AWS Instance ID
    hpc_autoscaling_group = current_app.autoscaling.describe_auto_scaling_groups(
        AutoScalingGroupNames=[HPC_AUTOSCALING_GROUP_NAME])['AutoScalingGroups']
    hpc_autoscaling_group_instances = hpc_autoscaling_group[0]['Instances']

    for instance in hpc_autoscaling_group_instances:
        instance_id = instance['InstanceId']
        instance_public_ip = current_app.ec2_client.describe_instances(
            InstanceIds=[instance_id])['Reservations'][0]['Instances'][0]['PublicIpAddress']
        if public_ip == instance_public_ip:
            return instance_id

    # We should not reach here.
    raise Exception('The given IP ({}) does not match any ID in the autoscaling group.'.format(
        public_ip))
