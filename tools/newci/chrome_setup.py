#!/usr/bin/env python

import json
import os.path
import re
import requests
from selenium import webdriver
from time import sleep

# allow Chrome to run headless
from pyvirtualdisplay import Display
display = Display(visible=0, size=(1024, 768))
display.start()

APP_HOST = "share.syncfs.com"
AEROFS_ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
LICENSE_FILE = os.path.join(AEROFS_ROOT_DIR, "packaging", "bakery", "development", "test.license")
ADMIN_USER = 'admin@syncfs.com'
BASE_URL = "https://{}".format(APP_HOST)
CODE_URL = "http://{}:21337/get_code".format(APP_HOST)
BOOTSTRAP_STATUS_URL = BASE_URL + '/json_get_bootstrap_task_status'

driver = webdriver.Chrome()
driver.get(BASE_URL)
print 'title: ', driver.title
print 'url: ', driver.current_url
assert "AeroFS" in driver.title

# License file page
driver.find_element_by_id('license-file').send_keys(LICENSE_FILE)
driver.find_element_by_id('continue-btn').click()
sleep(2)

# Hostname page
host_input = driver.find_element_by_id('base-host-unified')
host_input.clear()
host_input.send_keys(APP_HOST)
driver.find_element_by_id('next-btn').click()
sleep(2)

# Managing accounts page; the default is "Use AeroFS to manage user accounts",
# which is what we want, so continue through this page.
driver.find_element_by_id('next-btn').click()
sleep(2)

# Email server page: the default is to use the local mail relay, which is what
# we want, so continue through this page.
driver.find_element_by_id('next-btn').click()
sleep(0.5)
driver.find_element_by_id('verification-to-email').send_keys('test@example.com')
driver.find_element_by_id('send-verification-code-button').click()
sleep(2)
verification_code = re.search('actualCode\s*=[^0-9]*(\d*)', driver.page_source).group(1)
driver.find_element_by_id('verification-code').send_keys(verification_code)
driver.find_element_by_id('continue-button').click()
sleep(0.5)
driver.find_element_by_id('continue-btn').click()
sleep(2)

# Browser certificate page
driver.find_element_by_id('next-btn').click()
sleep(2)

# Apply and Finish; wait for bootstrap to finish
driver.find_element_by_id('finish-btn').click()
sleep(120)

# Create admin@syncfs.com
driver.find_element_by_id('start-create-user-btn').click()
sleep(2)
driver.find_element_by_id('create-user-email').send_keys(ADMIN_USER)
driver.find_element_by_id('create-user-btn').click()

# Get signup code
code = None
while code is None:
    sleep(1)
    r = requests.get(CODE_URL, params={"userid": ADMIN_USER}, verify=False)
    try:
        code = json.loads(r.text)["signup_code"]
    except ValueError:
        # could not find signup code
        pass

# Sign up via web
driver.get('{}/signup?c={}'.format(BASE_URL, code))
driver.find_element_by_id('inputFirstName').send_keys('Viktor')
driver.find_element_by_id('inputLastName').send_keys('Frankl')
driver.find_element_by_id('inputPasswd').send_keys('temp123')
driver.find_element_by_id('submitButton').click()
sleep(5)
driver.close()

