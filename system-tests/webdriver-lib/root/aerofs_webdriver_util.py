import time
from selenium.webdriver.support import expected_conditions as EC
from webdriver_util import wait_and_get
import requests
from urllib.parse import urlencode

LICENSE_FILE = '/test.license'


def upload_license(driver, selector, wait):
    """
    This method requires the AeroFS license is present at LICENSE_FILE
    """
    wait.until(EC.title_contains('Sign In to Manage Appliance'))

    # Make the license-file input visible. Invisible elements can't be interacted with
    input_id = 'license-file'
    driver.execute_script("document.getElementById('{}').style.display='block';".format(input_id))

    selector.get('#' + input_id).send_keys(LICENSE_FILE)


def login(driver, wait, selector, host, user, password):
    wait_and_get(driver, "https://{}/files".format(host))
    wait.until(EC.title_contains('Log In | AeroFS'))
    selector.get_and_clear('#input_email').send_keys(user)
    selector.get_and_clear('#input_passwd').send_keys(password)
    selector.get('#login_button').click()


def login_as_admin_at_syncfs_dot_com(driver, wait, selector, host):
    login(driver, wait, selector, host, 'support@aerofs.com', 'temp123')


def get_signup_code(hostname, user_id):
    """
    Get the signup code of the given user. This method requires the appliance runs Signup Decoder service, which is only
    available in the CI environment (see ci-cloud-config.yml)
    """
    url = "http://{}:21337/get_code?{}".format(hostname, urlencode({'userid': user_id}))
    print("Getting signup code via Signup Decoder at {}...".format(url))
    n = 15
    while True:
        r = requests.get(url)
        if r.status_code == 200:
            break
        n = n - 1
        if n == 0:
            r.raise_for_status()
        time.sleep(0.1)
    return r.json()['signup_code']

def invite_user(driver, wait, selector, host, user):
    login_as_admin_at_syncfs_dot_com(driver, wait, selector, host)
    wait.until(EC.title_contains('My Files'))
    selector.get_and_clear('#invite-coworker-email').send_keys(user)
    selector.get('#invite-coworker-submit').click()
    wait.until_display('#flash-msg-success', timeout=5 * 60)
