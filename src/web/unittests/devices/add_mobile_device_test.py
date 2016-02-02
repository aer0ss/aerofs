import struct
import base64
import hashlib
from aerofs_sp.gen.sp_pb2 import MobileAccessCode
from mock import Mock

from ..test_base import TestBase


class AddMobileDeviceTest(TestBase):
    def setUp(self):
        self.setup_common()

        code = MobileAccessCode(accessCode = 'fake_oauth_token')
        self.sp_rpc_stub.get_access_code_for_mobile = Mock(return_value=code)


    ### TESTS ###

    def test_generates_qrcode(self):
        """
        Test that we generate valid images when requesting QR codes.

        N.B.: We don't actually test that the images are valid QR codes, just that some image is returned, but this in
        conjuction with the other tests that we have here should provide a fairly good guarantee the the image is indeed
        a valid code.
        """
        from web.views.devices.add_mobile_device_view import get_mobile_access_code

        request = self.create_request('123', 'qrcode')
        self.check_valid_png(get_mobile_access_code(request))

    def test_generates_text_code(self):
        """
        Test that we generate valid text codes.
        We deserialize the returned code and check that everything matches what clients expect
        """
        from web.views.devices.add_mobile_device_view import get_mobile_access_code, MAGIC_BYTE

        request = self.create_request('123', 'text')
        response = get_mobile_access_code(request)

        self.assertEquals(response.status_code, 200)
        self.assertEquals(response.content_type, 'text/plain')

        code = base64.b64decode(response.body)

        # Check that the message starts with the magic byte
        magic = self.consume(code, 1)
        self.assertEquals(magic, MAGIC_BYTE)

        # Check that we then have the CA cert hash
        raw_cert = base64.b64decode(''.join(CACERT.replace(' ', '').split()[1:-1]))
        cert_hash = hashlib.sha256(raw_cert).digest()
        self.deserialize_and_check_field(code, cert_hash)

        # Check that we then have the config url
        self.deserialize_and_check_field(code, CONFIG_URL)

        # Check that we then have the oauth token
        self.deserialize_and_check_field(code, 'fake_oauth_token')

        # Check that there's nothing else
        nothing_else = self.consume(code, 1)
        self.assertEquals(nothing_else, '')

    def test_serialize(self):
        from web.views.devices.add_mobile_device_view import serialize, MAGIC_BYTE

        # Test various concatenations, including edge cases with empty strings
        self.assertEquals(serialize(''), MAGIC_BYTE + chr(0) + chr(0))
        self.assertEquals(serialize('aaa'), MAGIC_BYTE + chr(3) + chr(0) + 'aaa')
        self.assertEquals(serialize('aaa', ''), MAGIC_BYTE + chr(3) + chr(0) + 'aaa' + chr(0) + chr(0))
        self.assertEquals(serialize('aaa', 'b'), MAGIC_BYTE + chr(3) + chr(0) + 'aaa' + chr(1) + chr(0) + 'b')
        self.assertEquals(serialize('', 'bb', 'ccc'),
                          MAGIC_BYTE + chr(0) + chr(0) + chr(2) + chr(0) + 'bb' + chr(3) + chr(0) + 'ccc')

        # Test that we properly encode the length over 2 bytes for strings larger than 255 bytes
        self.assertEquals(serialize(256 * 'z'), MAGIC_BYTE + chr(0) + chr(1) + 256 * 'z')

        # Test that we handle unicode strings with binary data

        # `foo` is a unicode string (which is different from a regular python string) and on top of that it also has
        # characters outside the 0-128 ASCII range.
        foo = u'\xd0\x05'
        self.assertEquals(serialize(foo), MAGIC_BYTE + chr(2) + chr(0) + chr(0xd0) + chr(5))

    ### HELPER FUNCTIONS ###

    def create_request(self, request_id, code_format):
        request = self.create_dummy_request({
            'format': code_format,
        })
        request.registry.settings['config.loader.base_ca_certificate'] = CACERT
        request.registry.settings['config.loader.configuration_service_url'] = CONFIG_URL
        return request

    def check_valid_png(self, response):
        """
        Check that the response is a valid png image
        """
        self.assertEquals(response.status_code, 200)
        self.assertEquals(response.content_type, 'image/png')
        self.assertGreater(response.content_length, 70)  # A PNG image can't really have less than 70 bytes.

    def deserialize_and_check_field(self, code, expected):
        field_len = struct.unpack('<H', self.consume(code, 2))[0]
        self.assertEquals(field_len, len(expected))

        field = self.consume(code, field_len)
        self.assertEquals(field, expected)

    cur_pos = 0

    def consume(self, string, chars):
        """
        Consumes and returns n characters from a string
        """
        result = string[self.cur_pos:self.cur_pos + chars]
        self.cur_pos += chars  # Ideally we would use a coroutine instead of an ugly class variable :)
        return result


CONFIG_URL = "https://foo.com:123"

# Some random root CA cert found in Ubuntu.
CACERT = """
-----BEGIN CERTIFICATE-----
MIIHPTCCBSWgAwIBAgIBADANBgkqhkiG9w0BAQQFADB5MRAwDgYDVQQKEwdSb290
IENBMR4wHAYDVQQLExVodHRwOi8vd3d3LmNhY2VydC5vcmcxIjAgBgNVBAMTGUNB
IENlcnQgU2lnbmluZyBBdXRob3JpdHkxITAfBgkqhkiG9w0BCQEWEnN1cHBvcnRA
Y2FjZXJ0Lm9yZzAeFw0wMzAzMzAxMjI5NDlaFw0zMzAzMjkxMjI5NDlaMHkxEDAO
BgNVBAoTB1Jvb3QgQ0ExHjAcBgNVBAsTFWh0dHA6Ly93d3cuY2FjZXJ0Lm9yZzEi
MCAGA1UEAxMZQ0EgQ2VydCBTaWduaW5nIEF1dGhvcml0eTEhMB8GCSqGSIb3DQEJ
ARYSc3VwcG9ydEBjYWNlcnQub3JnMIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIIC
CgKCAgEAziLA4kZ97DYoB1CW8qAzQIxL8TtmPzHlawI229Z89vGIj053NgVBlfkJ
8BLPRoZzYLdufujAWGSuzbCtRRcMY/pnCujW0r8+55jE8Ez64AO7NV1sId6eINm6
zWYyN3L69wj1x81YyY7nDl7qPv4coRQKFWyGhFtkZip6qUtTefWIonvuLwphK42y
fk1WpRPs6tqSnqxEQR5YYGUFZvjARL3LlPdCfgv3ZWiYUQXw8wWRBB0bF4LsyFe7
w2t6iPGwcswlWyCR7BYCEo8y6RcYSNDHBS4CMEK4JZwFaz+qOqfrU0j36NK2B5jc
G8Y0f3/JHIJ6BVgrCFvzOKKrF11myZjXnhCLotLddJr3cQxyYN/Nb5gznZY0dj4k
epKwDpUeb+agRThHqtdB7Uq3EvbXG4OKDy7YCbZZ16oE/9KTfWgu3YtLq1i6L43q
laegw1SJpfvbi1EinbLDvhG+LJGGi5Z4rSDTii8aP8bQUWWHIbEZAWV/RRyH9XzQ
QUxPKZgh/TMfdQwEUfoZd9vUFBzugcMd9Zi3aQaRIt0AUMyBMawSB3s42mhb5ivU
fslfrejrckzzAeVLIL+aplfKkQABi6F1ITe1Yw1nPkZPcCBnzsXWWdsC4PDSy826
YreQQejdIOQpvGQpQsgi3Hia/0PsmBsJUUtaWsJx8cTLc6nloQsCAwEAAaOCAc4w
ggHKMB0GA1UdDgQWBBQWtTIb1Mfz4OaO873SsDrusjkY0TCBowYDVR0jBIGbMIGY
gBQWtTIb1Mfz4OaO873SsDrusjkY0aF9pHsweTEQMA4GA1UEChMHUm9vdCBDQTEe
MBwGA1UECxMVaHR0cDovL3d3dy5jYWNlcnQub3JnMSIwIAYDVQQDExlDQSBDZXJ0
IFNpZ25pbmcgQXV0aG9yaXR5MSEwHwYJKoZIhvcNAQkBFhJzdXBwb3J0QGNhY2Vy
dC5vcmeCAQAwDwYDVR0TAQH/BAUwAwEB/zAyBgNVHR8EKzApMCegJaAjhiFodHRw
czovL3d3dy5jYWNlcnQub3JnL3Jldm9rZS5jcmwwMAYJYIZIAYb4QgEEBCMWIWh0
dHBzOi8vd3d3LmNhY2VydC5vcmcvcmV2b2tlLmNybDA0BglghkgBhvhCAQgEJxYl
aHR0cDovL3d3dy5jYWNlcnQub3JnL2luZGV4LnBocD9pZD0xMDBWBglghkgBhvhC
AQ0ESRZHVG8gZ2V0IHlvdXIgb3duIGNlcnRpZmljYXRlIGZvciBGUkVFIGhlYWQg
b3ZlciB0byBodHRwOi8vd3d3LmNhY2VydC5vcmcwDQYJKoZIhvcNAQEEBQADggIB
ACjH7pyCArpcgBLKNQodgW+JapnM8mgPf6fhjViVPr3yBsOQWqy1YPaZQwGjiHCc
nWKdpIevZ1gNMDY75q1I08t0AoZxPuIrA2jxNGJARjtT6ij0rPtmlVOKTV39O9lg
18p5aTuxZZKmxoGCXJzN600BiqXfEVWqFcofN8CCmHBh22p8lqOOLlQ+TyGpkO/c
gr/c6EWtTZBzCDyUZbAEmXZ/4rzCahWqlwQ3JNgelE5tDlG+1sSPypZt90Pf6DBl
Jzt7u0NDY8RD97LsaMzhGY4i+5jhe1o+ATc7iwiwovOVThrLm82asduycPAtStvY
sONvRUgzEv/+PDIqVPfE94rwiCPCR/5kenHA0R6mY7AHfqQv0wGP3J8rtsYIqQ+T
SCX8Ev2fQtzzxD72V7DX3WnRBnc0CkvSyqD/HMaMyRa+xMwyN2hzXwj7UfdJUzYF
CpUCTPJ5GhD22Dp1nPMd8aINcGeGG7MW9S/lpOt5hvk9C8JzC6WZrG/8Z7jlLwum
GCSNe9FINSkYQKyTYOGWhlC0elnYjyELn8+CkcY7v2vcB5G5l1YjqrZslMZIBjzk
zk6q5PYvCdxTby78dOs6Y5nCpqyJvKeyRKANihDjbPIky/qbn3BHLt4Ui9SyIAmW
omTxJBzcoTWcFbLUvFUufQb1nA5V9FrWk9p2rSVzTMVD
-----END CERTIFICATE-----
"""