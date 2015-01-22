from inspect import stack
from os.path import join
from selenium.webdriver.support.ui import WebDriverWait
from time import time


class ElementCSSSelector:
    def __init__(self, d):
        self.d = d

    def get(self, selector):
        return self.d.find_element_by_css_selector(selector)

    def get_and_clear(self, selector):
        elem = self.get(selector)
        elem.clear()
        return elem


class Waiter:
    """
    A wrapper around WebDriverWait. It prints messages before the call and take a screenshot afterward. It also adds
    a few convenient functions.
    """

    def __init__(self, d, screenshot_out_dir, default_timeout=10):
        self.d = d
        self.shot_id = 0
        self.shot_dir = screenshot_out_dir
        self.default_timeout = default_timeout

    def until(self, method, message='', timeout=-1, caller_frame=2):
        if timeout < 0:
            timeout = self.default_timeout
        self._wrapper(method, message, timeout, caller_frame,
                      lambda mthd, msg: WebDriverWait(self.d, timeout).until(mthd, msg))

    def until_display(self, selector, timeout=-1):
        """
        For some reason EC.visibility_of throws exceptions. Hence this method.
        """
        self.until(ec_element_to_be_displayed(selector), timeout=timeout, caller_frame=3)

    def _wrapper(self, method, message, timeout, caller_frame, func):
        caller = stack()[caller_frame][3]
        print "Waiting in {}(), timeout {} secs...".format(caller, timeout)
        start = time()
        func(method, message)
        print "Spent {0:.3f} secs".format(time() - start)
        self.shoot(caller)

    def shoot(self, base_file_name):
        """
        Save a screenshot at {screenshot_out_dir}/N-{base_file_name}.png, where N is an incrementing integer ID.
        """
        file_name = '{}-{}.png'.format(self.shot_id, base_file_name)
        self.d.save_screenshot(join(self.shot_dir, file_name))
        self.shot_id += 1


########
# Expected conditions
#

def ec_element_to_be_displayed(selector):
    def ec(d):
        return ElementCSSSelector(d).get(selector).value_of_css_property('display') != 'none'
    return ec
