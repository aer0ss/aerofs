import base64
import requests
import tempfile
from cStringIO import StringIO

import properties

class Configuration(object):
    """
    Class that fetches and manages configuration.
    """
    def __init__(self, base_url, custom_cert=None, custom_cert_bundle=None):
        self.base_url = base_url
        # To allow for custom certificate use, we allow a custom_cert parameter
        # (raw PEM-encoded bytes), or a custom_cert_bundle parameter (filename)
        self.custom_cert = custom_cert
        self.custom_cert_bundle = custom_cert_bundle

    def _get(self, url):
        r = None
        if self.custom_cert_bundle:
            r = requests.get(url, verify=self.custom_cert_bundle)
        elif self.custom_cert:
            # Python's urllib SSL bindings do not allow specifying cacerts/bundles
            # as bytebuffers; they allow only filenames.  So create a named tempfile
            # to work around this limitation.  There's a small race condition here
            # that could potentially be exploited by a determined attacker, but this
            # is still better than verify=None, which is the alternative.
            with tempfile.NamedTemporaryFile() as f:
                f.write(self.custom_cert)
                f.seek(0)
                r = requests.get(url, verify=f.name)
        else:
            r = requests.get(url)
        if not r.ok:
            raise IOError("Couldn't reach configuration server: {}".format(
                r.status_code))
        return r.text

    def _parse_props(self, text):
        configuration = {}
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
        return configuration

    def client_properties(self):
        """
        Fetches the 'client' configuration from the config server and returns a
        dictionary of the config's key-value pairs.
        """
        url = "{}/client".format(self.base_url)
        text = self._get(url)
        configuration = self._parse_props(text)
        return configuration

    def server_properties(self):
        """
        Fetches the 'server' configuration from the config server and returns a
        dictionary of the config's key-value pairs.
        """
        url = "{}/server".format(self.base_url)
        text = self._get(url)
        configuration = self._parse_props(text)
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
