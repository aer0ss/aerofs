import re
import requests
from flask import current_app
from lizard import celery, db
from models import HPCDeployment, HPCServer
from docker import Client, tls
from sqlalchemy.exc import IntegrityError
import botocore.exceptions
from hpc_config import configure_deployment, reboot, repackage


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


def create_deployment(customer, subdomain):
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

    ).apply_async()


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

    # Wait up to an hour for hpc-sail to finish. It can take that long if hpc-sail needs to pull new images.
    try:
        exit_code = docker.wait(container_id, 3600)
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
        current_app.logger.warn("Ignoring exception while deleting deployment: %s", ex)

    delete_subdomain(deployment)

    db.session.delete(deployment)
    db.session.commit()


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
