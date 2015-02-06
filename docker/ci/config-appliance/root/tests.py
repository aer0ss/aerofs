from os.path import abspath
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.common.by import By
from tempfile import mkstemp
import yaml
import requests
from util import ElementCSSSelector
from urllib import urlencode

def upload_license(e, d, wait, license_file):

    wait.until(EC.title_contains('Sign In to Manage Appliance'))

    # Make the license-file input visible. Invisible elements can't be interacted with
    input_id = 'license-file'
    d.execute_script("document.getElementById('{}').style.display='block';".format(input_id))

    # Upload the file. Use absolute path for browser/OS portability.
    e.get('#' + input_id).send_keys(abspath(license_file))


def select_new_appliance(e, wait):
    wait.until(EC.text_to_be_present_in_element((By.TAG_NAME, 'p'), 'Please select your next step:'))

    # Click "Create a New Appliance"
    e.get('.btn-primary').click()


def set_hostname(e, wait, hostname):
    wait.until(EC.text_to_be_present_in_element((By.TAG_NAME, 'h3'), 'Step 1 of 3'))

    # Fill hostname
    e.get_and_clear('#base-host-unified').send_keys(hostname)

    _click_next(e)

    # Confirm firewall rules
    wait.until_display('#confirm-firewall-modal')
    e.get('#confirm-firewall-modal .btn-primary').click()


def set_browser_cert(e, wait, cert, key):
    wait.until(EC.text_to_be_present_in_element((By.TAG_NAME, 'h3'), 'Step 2 of 3'))

    # Select Upload new cert
    e.get('#cert-option-new').click()

    # Upload cert/key files
    _, cert_file = mkstemp()
    with open(cert_file, 'w') as f:
        f.write(cert)
    _, key_file = mkstemp()
    with open(key_file, 'w') as f:
        f.write(key)
    e.get('#cert-selector').send_keys(abspath(cert_file))
    e.get('#key-selector').send_keys(abspath(key_file))

    _click_next(e)


def set_email(e, wait, admin_email):
    wait.until(EC.text_to_be_present_in_element((By.TAG_NAME, 'h3'), 'Step 3 of 3'))

    # Set support email address
    e.get_and_clear('#base-www-support-email-address').send_keys(admin_email)

    _click_next(e)

    # Fill test email address
    wait.until_display('#verify-modal-email-input')
    e.get_and_clear('#verification-to-email').send_keys(admin_email)

    # Click "Send Verification Code"
    e.get('#send-verification-code-button').click()

    # Get verification code
    wait.until_display('#verify-modal-code-input')
    code = e.get('#verify-modal-email-input input[name="verification-code"]').get_attribute('value')

    # Fill the code and click Verify
    e.get_and_clear('#verification-code').send_keys(code)
    e.get('#continue-button').click()

    # Click Continue
    wait.until_display('#verify-succeed-modal')
    e.get('#verify-succeed-modal .btn-primary').click()


def _click_next(e):
    e.get('#next-btn').click()


def apply_config(e, wait):
    wait.until(EC.text_to_be_present_in_element((By.TAG_NAME, 'h3'), 'Please wait'))

    # Click Apply
    e.get('.btn-primary').click()
    print "The next step may take a while but should be less than five minutes:"

    # Click Create First User
    wait.until_display('#success-modal', timeout=30 * 60)
    e.get('#success-modal .btn-primary').click()

    # Wait for "Create First User" to show up then Click Continue
    wait.until(EC.title_contains('Create First Account'))
    e.get('#create-user-btn').click()

    # Wait until done
    wait.until_display('#email-sent-modal')


def get_signup_code(hostname, user_id):
    url = "http://{}:21337/get_code?{}".format(hostname, urlencode({'userid': user_id}))
    print "Getting signup code via Signup Decoder at {}...".format(url)
    r = requests.get(url)
    r.raise_for_status()
    return r.json()['signup_code']


def create_account(e, wait, password):
    wait.until(EC.title_contains('Create Account'))

    e.get_and_clear('#inputFirstName').send_keys('CI')
    e.get_and_clear('#inputLastName').send_keys('CI')
    e.get_and_clear('#inputPasswd').send_keys(password)
    e.get('#submitButton').click()

    wait.until(EC.title_contains('Download'))


def run_all(d, wait, hostname, license_file, appliance_setup_yml_file):

    with open(appliance_setup_yml_file) as f:
        y = yaml.load(f)

    url = "http://" + hostname
    print "Interacting with {}...".format(url)
    d.get(url)

    e = ElementCSSSelector(d)

    # Set up appliance
    upload_license(e, d, wait, license_file)
    select_new_appliance(e, wait)
    set_hostname(e, wait, hostname)
    set_browser_cert(e, wait, y['browser-cert'], y['browser-key'])
    set_email(e, wait, y['admin-email'])
    apply_config(e, wait)

    # Create first admin account
    code = get_signup_code(hostname, y['admin-email'])
    url = "https://{}/signup?c={}".format(hostname, code)
    print "Interacting with {}...".format(url)
    d.get(url)
    create_account(e, wait, y['admin-pass'])

    wait.shoot('end')
