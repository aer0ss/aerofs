from sys import argv
from selenium.webdriver.support import expected_conditions as EC
from webdriver_util import init, wait_and_get
from aerofs_webdriver_util import upload_license


driver, wait, selector = init()
driver.get("https://{}/admin/timekeeping".format(argv[1]))

# Log in
upload_license(driver, selector, wait)
wait.until(EC.title_contains('Timekeeping'))

# Set time server
selector.get_and_clear('#ntp-server').send_keys('time.nist.gov')
selector.get('#save-btn').click()
wait.until_display('#flash-msg-success')

# Clear time server
selector.get_and_clear('#ntp-server').send_keys('')
selector.get('#save-btn').click()
wait.until_display('#flash-msg-success')
