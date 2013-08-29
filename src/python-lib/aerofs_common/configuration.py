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

    """
    Initialize the configuration object.

    Params:
        config_url_file:
            A file that contains the configuration URL we must attach to.
        cacert_file:
            The trusted certificate for the https configuration service.
    """
    def __init__(self, config_url_file, cacert_file):
        self.config_url_file = config_url_file
        self.cacert_file = cacert_file

        # Read URL from file. In the case that the file does not exist,
        # config_url will remain null and we will fail on calls to other class
        # functions.
        self.config_url = None

        with open(config_url_file) as f:
            self.config_url = f.read().strip()

    """
    Fetch and populate the configuration values specified by the configuration
    URL and store those values in the pass configuration dictionary.

    Params:
        configuration:
            A dictionary that we will use to store the configuration values
            pulled from the server.
    """
    def fetch_and_populate(self, configuration):

        # Pull down configuration values and store them in our temporary dictionary.
        tmp = {}
        res = requests.get(self.config_url, verify=self.cacert_file)
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

    """
    Sets a key value pair on the persistent configuration server.

    Params:
        context:
            One of common, client, or server. Provides the key context (i.e. is
            key only used by the server? By the client? By both?).
        key:
            The key to set.
        value:
            The value to set.
    """
    def set_persistent_value(self, context, key, value):
        payload = {'context': context, 'key': key, 'value': value}
        requests.post(self.config_url, data=payload)
