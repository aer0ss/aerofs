import base64
import requests
import properties
from cStringIO import StringIO

class Configuration(object):
    """
    Class that fetches and manages configuration.
    """
    def __init__(self, base_url):
        self.base_url = base_url

    def _get(self, url):
        r = requests.get(url)
        if not r.ok:
            raise IOError("Couldn't reach configuration server: {}".format(
                r.status_code))
        return r.text

    def server_properties(self):
        """
        Fetches the 'server' configuration from the config server and returns a
        dictionary of the config's key-value pairs.
        """
        configuration = {}
        url = "{}/server".format(self.base_url)
        text = self._get(url)

        sio = StringIO(text)
        try:
            props = properties.Properties()
            props.load(sio)
        finally:
            sio.close()

        for key in props.propertyNames():
            value = props.getProperty(key)
            # Convert unicode strings returned from props to ascii. This is
            # required as client code uses ascii as keys to query properties.
            configuration[key.replace(b'\0', "")] = value.replace(b'\0', "")

        # Exclude the browser key to avoid keeping it around in the python
        # process's memory, in case a stacktrace somewhere winds up doing a
        # state dump and exposing the SSL key
        if 'server.browser.key' in configuration:
            del configuration['server.browser.key']
        return configuration

    def set_external_property(self, key, value):
        """
        Sets an external property on the persistent configuration server.
        Sets a key value pair on the persistent configuration server.

        @param key the key to set.
        @param value the value to set.
        """
        payload = {'key': key, 'value': value}
        url = "{}/set".format(self.base_url)
        requests.post(url, data=payload)

    def set_license(self, license_bytes):
        url = "{}/set_license_file".format(self.base_url)
        r = requests.post(url, data={
            'license_file': base64.urlsafe_b64encode(license_bytes)
        })
        r.raise_for_status()
        return r.text

    def is_license_shasum_valid(self, shasum):
        url = "{}/check_license_sha1".format(self.base_url)
        r = requests.get(url, params={
            'license_sha1': shasum
        })
        r.raise_for_status()
        return r.text
