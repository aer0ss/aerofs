import array
from pprint import pprint
import re
import requests
import jprops
from cStringIO import StringIO

"""
Class that fetches and manages configuration.
"""
class Configuration(object):

    def __init__(self, config_url_file, cacert_file):
        self.config_url_file = config_url_file
        self.cacert_file = cacert_file

    def fetch_and_populate(self, configuration):
        # Read URL from file.
        config_url = None
        with open(self.config_url_file) as f:
            config_url = f.read().strip()

        # Pull down configuration values and store them in our temporary dictionary.
        tmp = {}
        res = requests.get(config_url, verify=self.cacert_file)
        if res.ok:
            props = jprops.load_properties(StringIO(res.text))
            for key in props:
                # XXX Need to fix up string string formatting coming out of jprops.
                tmp[key.replace(b'\0', "")] = props[key].replace(b'\0', "")
        else:
            raise IOError("Couldn't reach configuration server: {}".format(res.status_code))

        # Resolve variable references. Just like in java land, we only support
        # one level of resolution.
        for key in tmp:
            # TODO (MP) need a cleaner way of excluding this value.
            if key == "server.browser.key":
                continue

            match = re.search(r'\${(.*)}', tmp[key])
            if match:
                configuration[key] = tmp[key].replace(match.group(0), tmp[match.group(1)])
            else:
                configuration[key] = tmp[key]
