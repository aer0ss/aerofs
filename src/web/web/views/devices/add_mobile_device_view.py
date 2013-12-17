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

def is_mobile_supported(settings):
    """
    Utility function that returns whether the 'add mobile device' view is supported
    @param settings: settings dictionary (request.registry.settings)
    """
    return is_private_deployment(settings)

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
    config_url = request.registry.settings.get('config.loader.configuration_service_url')
    oauth_token = generate_mobile_access_code(request)

    assert cacert
    assert config_url
    assert oauth_token

    cert_hash = hashlib.sha256(cacert).digest()

    code = serialize(cert_hash, config_url, oauth_token)

    # Return the access code in the appropriate format
    if token_format == 'qrcode':

        # `qrcode` is a pure python library. This makes it easy to install, but it's also incredibly slow (I suspect
        # something is really unoptimized because it takes about a second for a version 20 code)
        # However, for small code sizes, the speed is acceptable (~120ms).
        #
        # Should we need a fast library, I've published `fastqrcode` on pypi: https://pypi.python.org/pypi/fastqrcode/
        # Problem is, it's a wrapper around libqrencode, but Ubuntu currently ships an outdated version (3 year old)
        # of libqrencode that isn't compatible with fastqrencode. So for ease of install, I'm reverting to `qrcode`.
        # Sigh...
        qr = qrcode.QRCode(
            error_correction=qrcode.constants.ERROR_CORRECT_L,
            box_size=3,
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


def get_cacert(request):
    """
    Return the CA cert in binary format
    """
    cacert = request.registry.settings.get('config.loader.base_ca_certificate')
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
