import boto3
import botocore.exceptions
import datetime
import os
import re
import requests
import subprocess
import time

from docker import Client, tls
from flask import current_app
from hpc_config import configure_deployment, reboot, repackage, new_authed_session, get_boot_id
from lizard import celery, db, notifications
from models import HPCDeployment, HPCServer, Admin
from sqlalchemy.exc import IntegrityError

EXPIRING_LICENSE_NOTIFICATION_BUFFER = 7
POLLING_INTERVAL = 2
HPC_SERVER_CONFIG_PATH = '/opt/lizard/hpc-server-config'

BACKUP_BUCKET = 'aerofs.hpc.backupfiles'
HPC_SQS_QUEUE_NAME = 'hpc_auto_scaling'


class DeploymentAlreadyExists(Exception):
    def __init__(self):
        self.msg = "Sorry, that name is already taken. Please try another name."
        Exception.__init__(self)

    def __str__(self):
        return repr(self.msg)


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


def create_deployment(customer, subdomain, delay=0):
    """
    Create a new deployment for a given customer at a given subdomain.
    """

    # Add the deployment to the DB

    server = pick_server()

    deployment = HPCDeployment()
    deployment.customer = customer
    deployment.subdomain = subdomain
    deployment.server = server
    deployment.set_days_until_expiry(30)
    deployment.setup_status = HPCDeployment.status.IN_PROGRESS

    db.session.add(deployment)

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
        configure_deployment.si(subdomain) |
        reboot.si(subdomain) |
        repackage.si(subdomain)

    ).apply_async(link_error=save_error.si(subdomain), countdown=delay)


@celery.task()
def save_error(subdomain):
    deployment = HPCDeployment.query.get(subdomain)
    deployment.setup_status = HPCDeployment.status.DOWN
    db.session.commit()


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


def delete_deployment(deployment):
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

    db.session.delete(deployment)
    db.session.commit()
    return True


def pick_server():
    """
    Returns a server suitable to create a new deployment on.
    Currently picks the least crowded server.
    Throws an exceptions if there are no servers available.
    """
    count_deployments = db.func.count(HPCDeployment.subdomain)
    server, count = db.session.query(HPCServer, count_deployments) \
        .outerjoin(HPCDeployment) \
        .group_by(HPCServer.id) \
        .order_by(count_deployments.asc()) \
        .limit(1) \
        .one()

    return server


def delete_server(server):
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

    #Download the backup file and storing it in a S3 bucket
    backup_file = session.get('/admin/download_backup_file')
    backup_name = set_backup_name(subdomain)
    backup_path = "/tmp/{}".format(backup_name)
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
    cloud_config_path = '{}/data/cloud-config'.format(HPC_SERVER_CONFIG_PATH)

    with open(cloud_config_path) as f:
        user_data = f.read()

    instance = current_app.ec2.create_instances(
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
        instance_status = current_app.ec2.meta.client.describe_instance_status(
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


def configure_server(instance):
    # To configure the server we need both to launch the 'configure_hpc_server.sh'
    # script
    private_ip = instance.private_ip_address
    config_script_path = '{}/configure_hpc_server.sh {}'.format(HPC_SERVER_CONFIG_PATH,
                                                                private_ip)
    subprocess.Popen([config_script_path], shell=True)

    # Attach the instance to an an autoscaling group
    attach_instance_autoscaling_group(instance.id)

    # Add the instance to the DB
    server = HPCServer()
    server.docker_url = 'https://{}:2376'.format(private_ip)
    server.public_ip = instance.public_ip_address
    db.session.add(server)
    db.session.commit()


@celery.task()
def launch_server(server_name):
    # Lauching the server
    # The name and the size of the new instance has to be changed
    instance = create_server('r3.2xlarge', server_name)
    configure_server(instance)

    return instance.id


@celery.task()
def check_sqs_notifications():
    # Connecting to the queue
    queue = current_app.sqs_resource(QueueName=HPC_SQS_QUEUE_NAME)

    # Get the number of message from the queue
    queue_attributes = current_app.sqs_client.get_queue_attributes(
        QueueUrl=queue.url,
        AttributeNames=['ApproximateNumberOfMessages'])
    number_messages_queue = int(queue_attributes['Attributes']['ApproximateNumberOfMessages'])

    # If there is a message in the queue, it means that the alarm notified us
    # that we have to create new a instance
    if number_messages_queue != 0:
        requests.post('localhost:8000/launch_server')
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
    credential_path = '{}/secrets/aws_credentials'.format(HPC_SERVER_CONFIG_PATH)
    if not os.path.isfile(credential_path):
        with open(credential_path, 'w') as cred:
            cred.write('AWSAccessKeyId={} \n'.format(current_app.config['HPC_AWS_ACCESS_KEY']))
            cred.write('AWSSecretKey={}'.format(current_app.config['HPC_AWS_SECRET_KEY']))


