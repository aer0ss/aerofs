from os.path import abspath
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.common.by import By
from tempfile import mkstemp
import yaml
from sys import argv, stderr
from webdriver_util import init
from aerofs_webdriver_util import upload_license, get_signup_code


def select_new_appliance(e, wait):
    wait.until(EC.text_to_be_present_in_element((By.TAG_NAME, 'p'), 'Please select your next step:'))

    # Click "Create a New Appliance"
    e.get('.btn-primary').click()


def set_hostname(e, wait, hostname):
    wait.until(EC.text_to_be_present_in_element((By.TAG_NAME, 'h3'), 'Step 1 of 5'))

    # Fill hostname
    e.get_and_clear('#base-host-unified').send_keys(hostname)

    _click_next(e)

    # Confirm firewall rules
    wait.until_display('#confirm-firewall-modal')
    e.get('#confirm-firewall-modal .btn-primary').click()


def set_browser_cert(e, wait, cert, key):
    wait.until(EC.text_to_be_present_in_element((By.TAG_NAME, 'h3'), 'Step 2 of 5'))

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


def set_email(e, wait, email_host, email_port, email_username, email_password, admin_email):
    wait.until(EC.text_to_be_present_in_element((By.TAG_NAME, 'h3'), 'Step 3 of 5'))

    # Set remote mail relay
    e.get('input[value="remote"]').click()
    e.get_and_clear('#email-sender-public-host').send_keys(email_host)
    e.get_and_clear('#email-sender-public-port').send_keys(email_port)
    e.get_and_clear('#email-sender-public-username').send_keys(email_username)
    e.get_and_clear('#email-sender-public-password').send_keys(email_password)
    e.get('#email-sender-public-enable-tls').click()

    # Set support email address
    e.get_and_clear('#base-www-support-email-address').send_keys(admin_email)

    _click_next(e)

    # Fill test email address
    wait.until_display('#verify-modal-email-input')
    e.get_and_clear('#verification-to-email').send_keys(admin_email)

    # Click "Send Verification Code"
    e.get('#send-verification-code-button').click()

    # Get verification code
    wait.until_display('#verify-modal-code-input', timeout=30)
    code = e.get('#verify-modal-email-input input[name="verification-code"]').get_attribute('value')

    # Fill the code and click Verify
    e.get_and_clear('#verification-code').send_keys(code)
    e.get('#continue-button').click()

    # Click "Continue"
    wait.until_display('#verify-succeed-modal')
    e.get('#verify-succeed-modal .btn-primary').click()


def _click_next(e):
    e.get('#next-btn').click()


def reboot_system(e, wait):
    wait.until(EC.text_to_be_present_in_element((By.TAG_NAME, 'h3'), 'Step 4 of 5'))

    e.get('.btn-primary').click()

    wait.until_display('#success-modal', timeout=10 * 60)
    e.get('#success-modal .btn-primary').click()


def repackage_installers(e, wait):
    wait.until(EC.text_to_be_present_in_element((By.TAG_NAME, 'h3'), 'Step 5 of 5'))

    e.get('.btn-primary').click()

    wait.until_display('#success-modal', timeout=2 * 60)
    e.get('#success-modal .btn-primary').click()


def signup_first_user(e, wait):
    # Wait for the Create First Account page to show up then Click Continue
    wait.until(EC.title_contains('Create First Account'))
    e.get('#create-user-btn').click()

    # Wait until done
    wait.until_display('#email-sent-modal')


def create_account(e, wait, password):
    wait.until(EC.title_contains('Create Account'))

    e.get_and_clear('#inputFirstName').send_keys('CI')
    e.get_and_clear('#inputLastName').send_keys('CI')
    e.get_and_clear('#inputPasswd').send_keys(password)
    e.get('#submitButton').click()

    wait.until(EC.title_contains('Download'))


def run_all(d, wait, e, hostname, create_first_user):

    with open('/setup.yml') as f:
        y = yaml.load(f)

    url = "http://" + hostname
    print "Interacting with {}...".format(url)
    d.get(url)

    # Set up appliance
    upload_license(d, e, wait)
    select_new_appliance(e, wait)
    set_hostname(e, wait, hostname)
    set_browser_cert(e, wait, y['browser-cert'], y['browser-key'])
    set_email(e, wait, y['email-host'], y['email-port'], y['email-username'], y['email-password'], y['admin-email'])
    reboot_system(e, wait)
    repackage_installers(e, wait)

    # Create first admin account
    if create_first_user:
        signup_first_user(e, wait)
        code = get_signup_code(hostname, y['admin-email'])
        url = "https://{}/signup?c={}".format(hostname, code)
        print "Interacting with {}...".format(url)
        d.get(url)
        create_account(e, wait, y['admin-pass'])

    wait.shoot('end')


def main():
    if len(argv) != 3:
        print >>stderr, "Usage: {} <hostname> <create-first-user>".format(argv[0])
        exit(11)

    driver, waiter, selector = init()
    run_all(driver, waiter, selector, argv[1], argv[2] == 'true')


main()
