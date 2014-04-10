import logging
import base64
import struct
import hashlib

import qrcode
from pyramid.view import view_config
from pyramid.response import Response

from web.util import *


log = logging.getLogger(__name__)

# Magic byte that we prefix to our qrcodes.
# Note: If you change this number, it will break sign-in with older mobile apps.
# !! Don't forget to update this number in the mobile apps as well !!
MAGIC_BYTE = chr(188)


@view_config(
    route_name='add_mobile_device',
    permission='user',
    renderer='add_mobile_device.mako'
)
def add_mobile_device(request):
    return {
        'qrcode_url': request.route_path('get_mobile_access_code',_query={'format': 'qrcode'}),
        'textcode_url': request.route_path('get_mobile_access_code',_query={'format': 'text'}),
    }


@view_config(
    route_name='get_mobile_access_code',
    permission='user',
)
def get_mobile_access_code(request):
    """
    Return a code that is used to configure and grant access to mobile devices

    Request parameters:
    `format`        Either 'qrcode' or 'text'

    Return:
    An image/png response with the qrcode if format is 'qrcode', or a text/plain response with the blob of text
    that the user must copy/paste into the mobile app.

    Example: GET https://unified.syncfs.com/devices/get_mobile_access_code?format=qrcode
    """

    # Parse arguments
    token_format = request.params.get('format')

    # Create access code
    cacert = get_cacert(request)
    config_url = request.registry.settings.get('config.loader.configuration_service_url', 'https://config.aerofs.com/')
    oauth_token = generate_mobile_access_code(request)

    assert cacert
    assert config_url
    assert oauth_token

    cert_hash = hashlib.sha256(cacert).digest()

    code = serialize(cert_hash, config_url, oauth_token)

    # Return the access code in the appropriate format
    if token_format == 'qrcode':
        qr = qrcode.QRCode(
            error_correction=qrcode.constants.ERROR_CORRECT_L,
            box_size=6,
            border=4,
        )
        qr.add_data(code)
        qr.make(fit=True)

        response = Response(content_type='image/png', cache_expires=0)
        qr.make_image().save(response.body_file, 'png')

    elif token_format == 'text':
        # We enclose the code between '[[[' and ']]]' because Safari on iOS copies the text as rich text with a
        # bunch of formatting crap. So we use a regexp to cut through it and find the code.
        text_code = '[[[' + base64.b64encode(code) + ']]]'
        response = Response(body=text_code, content_type='text/plain', cache_expires=0)

    else:
        raise ValueError("format must be 'qrcode' or 'text'")

    return response


def generate_mobile_access_code(request):
    sp = get_rpc_stub(request)
    reply = sp.get_mobile_access_code()
    return reply.accessCode

# The CA cert for public deployment. Ideally we should read this cert from the file rather than hard coding it here.
# However, because we plan to use the configuration server (config.aerofs.com) for public deployment soon and thus
# obsolete this code, I (WW) didn't bother spending time implementing it.
_PUBLIC_DEPLOYMENT_CACERT = \
    '-----BEGIN CERTIFICATE-----\\n' \
    'MIID5TCCAs2gAwIBAgIJAMglwusARYgmMA0GCSqGSIb3DQEBCwUAMIGAMQ8wDQYD\\n' \
    'VQQDEwZBZXJvRlMxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYw\\n' \
    'FAYDVQQHEw1TYW4gRnJhbmNpc2NvMRMwEQYDVQQKEwphZXJvZnMuY29tMR4wHAYJ\\n' \
    'KoZIhvcNAQkBFg90ZWFtQGFlcm9mcy5jb20wHhcNMTMwNTAxMjI0NDU1WhcNMjMw\\n' \
    'NDI5MjI0NDU1WjCBgDEPMA0GA1UEAxMGQWVyb0ZTMQswCQYDVQQGEwJVUzETMBEG\\n' \
    'A1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNU2FuIEZyYW5jaXNjbzETMBEGA1UE\\n' \
    'ChMKYWVyb2ZzLmNvbTEeMBwGCSqGSIb3DQEJARYPdGVhbUBhZXJvZnMuY29tMIIB\\n' \
    'IjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzhK5CxtoqaXEcD0Bq0TG9ExT\\n' \
    'FleIkx4hnblv6OK4Wm6EzA4wo9odeg8/3Jyd0b3cO1QuGAcUydzuknNNi7UMKKvO\\n' \
    'WPCRpQxdgIqWis7yziJ2XVv8GVdJ+YfTeYgkDYheLj0H8Qbv1pvYC2c0yuELb72W\\n' \
    'Lr4dcqnqPgwEROSftcEB07G1SuyvkimEO5nRkAcSrTA0nSomKhNGT2IgkbG08vWE\\n' \
    '0tU5AeoefVGjAOygnMgveJg6aO9nrGxDB4Qg2DcJPZPlOt4JIARls6cwR3GGn07w\\n' \
    'Uw68sdjCHLQcuQObfwJnar8W2Eu/1xSLoCYgVwNyd0sS3QtSkMzkB1dzo7I7sQID\\n' \
    'AQABo2AwXjAdBgNVHQ4EFgQUyypjgG7zMXZKq0Z+Y7RUWaeNEtMwHwYDVR0jBBgw\\n' \
    'FoAUyypjgG7zMXZKq0Z+Y7RUWaeNEtMwDwYDVR0TAQH/BAUwAwEB/zALBgNVHQ8E\\n' \
    'BAMCAQYwDQYJKoZIhvcNAQELBQADggEBACpuypRbS1IUqJtEOo3gXZ0YGPiS23ex\\n' \
    '5giLD95JThkQYB7e4iQIsPnPDQ0migNkRumuSIDypr4gUfrPuqt7oVjTDvWUsFGF\\n' \
    'DoTOEBNT3CcFkeeDa6nmGT+y5GaPEQ7Zp8yQTv7dg96Tg/5tHc1wGHO7slqZdzVU\\n' \
    'oUjS7N0idzBBFtug2+TM9ZOV36czK5NS1EIPTr47Pnyt+35hs2vqcJv6/z1510vm\\n' \
    'ENfCjHBsgZ1Sysw04FX9hkkWEgQkUe4Q3JZ2+CbSego9t2uqs8MGNzQSy7I+yi4+\\n' \
    'U9nQmzLWYykodsNPn7erKjxjmqM77qFnyckKvbPV2gR5zvvU5UGq/bo=\\n' \
    '-----END CERTIFICATE-----'

def get_cacert(request):
    """
    Return the CA cert in binary format
    """
    cacert = request.registry.settings.get('config.loader.base_ca_certificate', _PUBLIC_DEPLOYMENT_CACERT)
    cacert = cacert.replace('\\n', '').replace('-----BEGIN CERTIFICATE-----','').replace('-----END CERTIFICATE-----','')
    return base64.b64decode(cacert)


def serialize(*args):
    """
    Serialize an arbitrary number of strings by prefixing each string with its length (2 bytes) and concatenating
    everything together.
    """

    length = lambda x: struct.pack('<H', len(x))  # return the length of x as 2 bytes, little-endian.

    return MAGIC_BYTE + ''.join(f(x) for x in args for f in (length, raw_str))


def raw_str(obj):
    """
    Return the byte string representation of obj
    """
    try:
        return str(obj)
    except UnicodeEncodeError:
        # This works because:
        #  1. encode() converts a Unicode string to a plain string
        #  2. The ISO 8859-1 encoding (aka Latin-1) maps the first 256 Unicode codepoints to their byte values.
        return unicode(obj).encode('latin1')
