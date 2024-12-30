from sys import argv, stderr
from selenium.webdriver.support import expected_conditions as EC
from webdriver_util import init, wait_and_get
from aerofs_webdriver_util import upload_license, login, invite_user


def get_ldap_server_cert(ldap_server_cert_filename):
    with open(ldap_server_cert_filename, 'r') as cert_file:
        return cert_file.read()


def enable_ldap_plaintext(w, e, ldap_address):
    w.until(EC.title_contains('Identity'))

    e.get('input[name="authenticator"][value="external_credential"]').click()
    # These values should be consistent with those defined in configure-opends.sh
    e.get_and_clear('#ldap-server-host').send_keys(ldap_address)
    e.get_and_clear('#ldap-server-port').send_keys('389')
    e.get_and_clear('#ldap-server-schema-user-base').send_keys('dc=example,dc=com')
    e.get_and_clear('#ldap-server-principal').send_keys('cn=root')
    e.get_and_clear('#ldap-server-credential').send_keys('aaaaaa')
    e.get('input[name="ldap_server_security"][value="none"]').click()

    save_ldap_settings(w, e)


def enable_ldap_tls(w, e, ldap_address, ldap_server_cert_pem):
    w.until(EC.title_contains('Identity'))

    e.get('input[name="authenticator"][value="external_credential"]').click()
    # These values should be consistent with those defined in configure-opends.sh
    e.get_and_clear('#ldap-server-host').send_keys(ldap_address)
    e.get_and_clear('#ldap-server-port').send_keys('389')
    e.get_and_clear('#ldap-server-schema-user-base').send_keys('dc=example,dc=com')
    e.get_and_clear('#ldap-server-principal').send_keys('cn=root')
    e.get_and_clear('#ldap-server-credential').send_keys('aaaaaa')
    e.get('input[name="ldap_server_security"][value="tls"]').click()
    e.get('#show-advanced-ldap-options').click()
    e.get_and_clear('#ldap-server-ca_certificate').send_keys(ldap_server_cert_pem)

    save_ldap_settings(w, e)


def enable_ldap_ssl(w, e, ldap_address, ldap_server_cert_pem):
    w.until(EC.title_contains('Identity'))

    e.get('input[name="authenticator"][value="external_credential"]').click()
    # These values should be consistent with those defined in configure-opends.sh
    e.get_and_clear('#ldap-server-host').send_keys(ldap_address)
    e.get_and_clear('#ldap-server-port').send_keys('636')
    e.get_and_clear('#ldap-server-schema-user-base').send_keys('dc=example,dc=com')
    e.get_and_clear('#ldap-server-principal').send_keys('cn=root')
    e.get_and_clear('#ldap-server-credential').send_keys('aaaaaa')
    e.get('input[name="ldap_server_security"][value="ssl"]').click()
    e.get('#show-advanced-ldap-options').click()
    e.get_and_clear('#ldap-server-ca_certificate').send_keys(ldap_server_cert_pem)

    save_ldap_settings(w, e)


def disable_ldap(w, e):
    w.until(EC.title_contains('Identity'))
    e.get('input[name="authenticator"][value="local_credential"]').click()
    save_ldap_settings(w, e)


def save_ldap_settings(w, e):
    e.get('#save-ldap').click()
    w.until_display('#success-modal', timeout=5 * 60)


USER = 'user.0@maildomain.net'
PASS = 'password'


def should_allow_ldap_user_login(d, w, e, host):
    login(d, w, e, host, USER, PASS)
    w.until(EC.title_contains('My Files'))


def should_forbid_ldap_user_login(d, w, e, host):
    login(d, w, e, host, USER, PASS)
    w.until_display('#flash-msg-error')


def main():
    if len(argv) != 4:
        print("Usage: {} <hostname> <ldap-server-address> <ldap-server-cert>".format(argv[0]), file=stderr)
        exit(11)
    ldap_server_cert_pem = get_ldap_server_cert(argv[3])
    driver, waiter, selector = init()
    invite_user(driver, waiter, selector, argv[1], USER)
    url = "https://{}/admin/identity".format(argv[1])
    wait_and_get(driver, url)
    upload_license(driver, selector, waiter)
    enable_ldap_plaintext(waiter, selector, argv[2])
    should_allow_ldap_user_login(driver, waiter, selector, argv[1])

    wait_and_get(driver, url)
    enable_ldap_tls(waiter, selector, argv[2], ldap_server_cert_pem)
    should_allow_ldap_user_login(driver, waiter, selector, argv[1])

    wait_and_get(driver, url)
    enable_ldap_ssl(waiter, selector, argv[2], ldap_server_cert_pem)
    should_allow_ldap_user_login(driver, waiter, selector, argv[1])

    wait_and_get(driver, url)
    disable_ldap(waiter, selector)
    should_forbid_ldap_user_login(driver, waiter, selector, argv[1])

main()
