from sys import argv, stderr
from selenium.webdriver.support import expected_conditions as EC
from webdriver_util import init, wait_and_get
from aerofs_webdriver_util import upload_license


def enable_ldap(w, e, ldap_address):
    w.until(EC.title_contains('Identity'))

    e.get('input[name="authenticator"][value="external_credential"]').click()
    # These values should be consistent with those defined in configure-opends.sh
    e.get_and_clear('#ldap-server-host').send_keys(ldap_address)
    e.get_and_clear('#ldap-server-port').send_keys('389')
    e.get_and_clear('#ldap-server-schema-user-base').send_keys('dc=example,dc=com')
    e.get_and_clear('#ldap-server-principal').send_keys('cn=root')
    e.get_and_clear('#ldap-server-credential').send_keys('aaaaaa')
    e.get('input[name="ldap_server_security"][value="none"]').click()

    apply_ldap_settings(w, e)


def disable_ldap(w, e):
    w.until(EC.title_contains('Identity'))
    e.get('input[name="authenticator"][value="local_credential"]').click()
    apply_ldap_settings(w, e)


def apply_ldap_settings(w, e):
    e.get('#save-btn').click()

    print
    print ">>> If the test runs against dev environment, please `dk-restart` now before time runs out."
    print

    w.until_display('#success-modal', timeout=5 * 60)


def should_allow_ldap_user_login(d, w, e, host):
    login(d, w, e, host)
    w.until(EC.title_contains('My Files'))


def should_forbid_ldap_user_login(d, w, e, host):
    login(d, w, e, host)
    w.until_display('#flash-msg-error')


def login(d, w, e, host):
    wait_and_get(d, "https://{}/files".format(host))
    w.until(EC.title_contains('Sign In'))
    e.get_and_clear('#input_email').send_keys('user.0@maildomain.net')
    e.get_and_clear('#input_passwd').send_keys('password')
    e.get('#signin_button').click()


def main():
    if len(argv) != 3:
        print >>stderr, "Usage: {} <hostname> <ldap-server-address>".format(argv[0])
        exit(11)
    driver, waiter, selector = init()

    url = "http://{}:8484/identity".format(argv[1])
    wait_and_get(driver, url)
    upload_license(driver, selector, waiter)
    enable_ldap(waiter, selector, argv[2])
    should_allow_ldap_user_login(driver, waiter, selector, argv[1])

    wait_and_get(driver, url)
    disable_ldap(waiter, selector)
    should_forbid_ldap_user_login(driver, waiter, selector, argv[1])


main()