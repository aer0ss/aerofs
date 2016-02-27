import re
import requests
from lizard import celery, db, notifications
from datetime import datetime, timedelta
import time
import models
from celery.utils.log import get_task_logger

logger = get_task_logger(__name__)

#
# This file contains the Celery tasks to configure an HPC deployment
#


class DeploymentSession(requests.Session):
    """
    This class extends requests.Session to make it easier to work with HPC deployments.
    """
    def __init__(self, deployment):
        super(DeploymentSession, self).__init__()
        self.deployment = deployment
        self.base_url = "https://" + deployment.full_hostname()

    def authenticate(self):
        self.set_csrf_token()
        self.set_license()

    def set_csrf_token(self):
        """
        Loads the login page and reads the CSRF token from the meta tag, then sets it on the session
        """
        r = self.get("/admin/login", timeout=5)
        r.raise_for_status()
        m = re.match('.*"csrf-token" content="([a-z0-9]+)"', r.text, re.DOTALL)
        if not m:
            raise Exception("csrf token not found!")

        token = m.groups()[0]
        self.headers.update({'X-CSRF-Token': token})

    def set_license(self):
        """
        Sets the license on the appliance. As a side effect, it also authenticates the session.
        """

        # Get the customer's license
        lic = self.deployment.customer.newest_filled_license()

        # The server expects the license as a UTF-8 encoded string, for compatibility reasons with Javascript.
        lic_data = lic.blob.decode('latin-1').encode('utf-8')

        r = self.post("/admin/json_set_license", data={"license": lic_data})
        r.raise_for_status()

    def get(self, url, **kwargs):
        # Overrides Requests' Session.get() to set the base url
        return super(DeploymentSession, self).get(self.base_url + url, **kwargs)

    def post(self, url, data=None, json=None, **kwargs):
        # Overrides Requests' Session.post() to set the base url
        return super(DeploymentSession, self).post(self.base_url + url, data, json, **kwargs)


def new_authed_session(subdomain):
    """
    Creates and returns a new authenticated DeploymentSession for the given subdomain
    """
    deployment = models.HPCDeployment.query.get(subdomain)
    if deployment is None:
        raise Exception("Deployment '{}' not found in the database".format(subdomain))

    session = DeploymentSession(deployment)
    session.authenticate()
    return session


@celery.task(bind=True)
def configure_deployment(self, subdomain):
    """
    Performs the appliance setup (hostname, certificates, email, etc..)
    """
    logger.info("Configuring deployment for subdomain: {}".format(subdomain))

    try:
        session = new_authed_session(subdomain)

        # Set the hostname (step 1 of setup)
        r = session.post("/admin/json_setup_hostname", data={'base.host.unified': session.deployment.full_hostname()})
        r.raise_for_status()

        # Set the certificate (step 2 of setup)
        r = session.post("/admin/json_setup_certificate", data={'cert.option': 'existing'})
        r.raise_for_status()

        # Configure email settings (step 3 of setup)
        r = session.post("/admin/json_setup_email",
                         data={'email-server': 'local',
                               'base-www-support-email-address': 'support@aerofs.com'})
        r.raise_for_status()

        # Set other HPC-related parameters (currently the Zephyr address, maybe more in the future)
        r = session.post("/admin/json_setup_hpc",
                         data={'zephyr.address': 'zephyr.aerofs.com:8888'})
        r.raise_for_status()

    except Exception as e:
        # Many errors are due to the fact that DNS hasn't propagated yet or the server is busy pulling Docker images
        # Wait 30s and retry
        raise self.retry(exc=e, countdown=30, max_retries=40)


@celery.task(bind=True)
def reboot(self, subdomain):
    """
    Reboot the appliance in default (ie: non-maintenance) mode
    """
    try:
        logger.info("Rebooting appliance at {}".format(subdomain))

        deadline = datetime.now() + timedelta(seconds=120)
        session = new_authed_session(subdomain)

        old_id = get_boot_id(session)
        if old_id is None:
            raise Exception("Couldn't get boot id from appliance at {}".format(session.base_url))

        logger.debug("Old boot id {}".format(old_id))

        # Reboot the appliance in default mode
        try:
            r = session.post("/admin/json-boot/default")
            if 400 <= r.status_code < 500:
                raise Exception("Rebooting appliance at {} failed with HTTP status code  {}".format(session.base_url, r.status_code))
        except requests.exceptions.RequestException:
            pass  # Ignore connection errors since the server might be killed before replying.

        # Wait for new boot id, or until we time out
        while True:
            new_id = get_boot_id(session)
            logger.debug("New id {}".format(new_id))
            if new_id is not None and new_id != old_id:
                break

            if datetime.now() > deadline:
                raise Exception("Timed out while waiting for appliance at {} to reboot".format(session.base_url))

            time.sleep(2)

    except Exception as e:
        raise self.retry(exc=e, countdown=40, max_retries=20)


def get_boot_id(session):
    """
    Returns the appliance's boot id, or None if the appliance is unreachable
    """
    try:
        r = session.get("/admin/json-get-boot", timeout=5)
    except requests.exceptions.RequestException:
        return None
    return r.json()['id'] if r.status_code == 200 else None


@celery.task(bind=True)
def repackage(self, subdomain):
    try:
        session = new_authed_session(subdomain)
        wait_for_services_ready(session)
        do_repackaging(session)
        session.post("/admin/json-set-configuration-completed")

        # Consider the appliance set up and running
        session.deployment.appliance_setup_date = datetime.today()
        session.deployment.set_days_until_expiry(30)
        db.session.commit()

        #Let them know they have an appliance
        admins = models.Admin.query.filter_by(
            customer_id=session.deployment.customer_id).all()
        for admin in admins:
            notifications.send_hpc_trial_setup_email(admin, session.base_url)

    except Exception as e:
        raise self.retry(exc=e, countdown=30, max_retries=10)


def wait_for_services_ready(session):
    """
    Waits at most 10 seconds for all our services to be up and running
    """
    logger.debug("Waiting for services to be ready on {}".format(session.base_url))

    deadline = datetime.now() + timedelta(seconds=10)

    while True:
        r = session.get("/admin/json-status")
        r.raise_for_status()

        statuses = r.json()['statuses']
        unhealthy = [s for s in statuses if not s['is_healthy'] and
                     s['service'] != 'team-servers' and
                     s['service'] != 'zephyr']

        if len(unhealthy) > 0:
                if datetime.now() > deadline:
                    raise Exception("Services {} are unhealthy on {}".format(unhealthy, session.base_url))
                else:
                    time.sleep(2)
        else:
            logger.debug("All services are ready.")
            return


def do_repackaging(session):
    """
    Performs repackaging
    """
    logger.info("Repackaging on {}".format(session.base_url))

    # TODO: Check that repacking hasn't already started
    r = session.post("/admin/json-repackaging")
    r.raise_for_status()

    while True:
        r = session.get("/admin/json-repackaging")
        r.raise_for_status()
        repackaging = r.json()
        logger.debug(repackaging)
        if repackaging['running']:
            time.sleep(2)
        elif repackaging['succeeded']:
            return
        else:
            raise Exception("Repacking failed at {}".format(session.base_url))


