from sys import argv, stderr
from selenium.webdriver.support import expected_conditions as EC
from webdriver_util import init, wait_and_get
from aerofs_webdriver_util import upload_license, login_as_admin_at_syncfs_dot_com


def enable_downstream(w, e, downstream_address, downstream_port):
    w.until(EC.title_contains('Auditing'))

    e.get('#audit-option-enable').click()
    e.get_and_clear('#audit-downstream-host').send_keys(downstream_address)
    e.get_and_clear('#audit-downstream-port').send_keys(downstream_port)

    save_downstream_settings(w, e)


def disable_downstream(w, e):
    w.until(EC.title_contains('Auditing'))
    e.get('#audit-option-disable').click()
    save_downstream_settings(w, e)


def save_downstream_settings(w, e):
    e.get('#save-btn').click()

    print
    print ">>> If the test runs against dev environment, please `dk-restart` now before time runs out."
    print

    w.until_display('#flash-msg-success', timeout=5 * 60)


def main():
    if len(argv) != 4:
        print >>stderr, "Usage: {} <hostname> <downstream-address> <downstream-port>".format(argv[0])
        exit(11)
    driver, waiter, selector = init()

    url = "http://{}:8484/auditing".format(argv[1])
    wait_and_get(driver, url)
    upload_license(driver, selector, waiter)
    enable_downstream(waiter, selector, argv[2], argv[3])
    login_as_admin_at_syncfs_dot_com(driver, waiter, selector, argv[1])
    wait_and_get(driver, url)
    disable_downstream(waiter, selector)


main()