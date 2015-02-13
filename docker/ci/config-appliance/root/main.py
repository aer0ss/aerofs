from sys import argv, stderr
import traceback
from util import Waiter
from selenium import webdriver
from selenium.common.exceptions import TimeoutException

from tests import run_all


if len(argv) != 5:
    print >>stderr, "Usage: {} <hostname-of-appliance-under-test> <screenshot-output-dir> <path-to-license-file> " \
                    "<path-to-appliance.yml>".format(argv[0])
    exit(11)

print "Loading Firefox driver..."
driver = webdriver.Firefox()
waiter = Waiter(driver, argv[2])

try:
    print "Running all tests..."
    run_all(driver, waiter, argv[1], argv[3], argv[4])
except TimeoutException, e:
    print >>stderr, traceback.format_exc()
    waiter.shoot('timeout-exception')
    exit(22)
