from selenium.webdriver.support import expected_conditions as EC
import requests
from urllib import urlencode

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


def get_signup_code(hostname, user_id):
    """
    Get the signup code of the given user. This method requires the appliance runs Signup Decoder service, which is only
    available in the CI environment (see ci-cloud-config.yml)
    """
    url = "http://{}:21337/get_code?{}".format(hostname, urlencode({'userid': user_id}))
    print "Getting signup code via Signup Decoder at {}...".format(url)
    r = requests.get(url)
    r.raise_for_status()
    return r.json()['signup_code']
