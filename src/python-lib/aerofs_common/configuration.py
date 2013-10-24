import re
import requests
import properties
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
        res = requests.get(SERVER_URL)
        if not res.ok:
            raise IOError("Couldn't reach configuration server: {}".format(res.status_code))

        sio = StringIO(res.text)
        try:
            props = properties.Properties()
            props.load(sio)
        finally:
            sio.close()

        fixed_props = {}
        for key in props.propertyNames():
            value = props.getProperty(key)
            # Convert unicode strings returned from props to ascii. This is
            # required as client code uses ascii as keys to query properties.
            fixed_props[key.replace(b'\0', "")] = value.replace(b'\0', "")

        # Exclude the browser key to avoid keeping it around in the python
        # process's memory, in case a stacktrace somewhere winds up doing a
        # state dump and exposing the SSL key
        del fixed_props['server.browser.key']

        # Resolve variable references. Just like in java land, we only support
        # one level of resolution.
        for key in fixed_props:
            value = fixed_props[key]
            match = re.search(r'\${(.*)}', value)
            if match:
                # The prop contains variable. Dereference it.
                configuration[key] = value.replace(match.group(0),
                                                 fixed_props[match.group(1)])
            else:
                # The prop doesn't contain variable.
                configuration[key] = value

    def set_external_property(self, key, value):
        """
        Sets an external property on the persistent configuration server.
        Sets a key value pair on the persistent configuration server.

        @param key the key to set.
        @param value the value to set.
        """
        payload = {'key': key, 'value': value}
        requests.post(SET_URL, data=payload)
