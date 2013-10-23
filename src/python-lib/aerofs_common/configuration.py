import array
from pprint import pprint
import re
import requests
import jprops
from cStringIO import StringIO

SERVER_URL = "http://localhost:5436/"
SET_URL = "http://localhost:5437/"

class Configuration(object):
    """
    Class that fetches and manages configuration.
    """

    def fetch_and_populate(self, configuration):
        """
        Fetch and populate the configuration values specified by the configuration
        URL and store those values in the passed configuration dictionary.

        Params:
            configuration:
                A dictionary that we will use to store the configuration values
                pulled from the server.
        """

        # Pull down configuration values and store them in our temporary dictionary.
        tmp = {}
        res = requests.get(SERVER_URL)
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

    def set_external_property(self, key, value):
        """
        Sets an external property on the persistent configuration server.
        Sets a key value pair on the persistent configuration server.

        @param key the key to set.
        @param value the value to set.
        """
        payload = {'key': key, 'value': value}
        requests.post(SET_URL, data=payload)
