import hashlib
import hmac

from Crypto.Cipher import AES
from Crypto.Protocol.KDF import PBKDF2
from Crypto import Random

# Things below here are crypto and should probably be moved somewhere more generic
_BLOCKSIZE = 16
def _pkcs7_pad(blob):
    bytes_short = 16 - (len(blob) % 16)
    return blob + (bytes_short) * chr(bytes_short)

def _pkcs7_unpad(blob):
    pad = ord(blob[-1])
    if blob[-pad:] != (pad * chr(pad)):
        raise ValueError("Invalid PKCS7 padding")
    return blob[:-pad]

def crypto_box(request, secret):
    aes_key = PBKDF2(request.registry.settings["session.encrypt_key"], "two_factor_salt", dkLen=16)
    iv = Random.new().read(AES.block_size)
    cipher = AES.new(aes_key, AES.MODE_CBC, iv)
    boxed_secret = iv + cipher.encrypt(_pkcs7_pad(secret))
    hmac_secret = request.registry.settings["session.validate_key"]
    mac = hmac.new(hmac_secret, boxed_secret, hashlib.sha1).digest()
    secret_blob = boxed_secret + mac
    return secret_blob

def _constant_time_is_equal(a, b):
    if len(a) != len(b):
        return False
    res = 0
    for i in xrange(len(a)):
        res |= (ord(a[i]) ^ ord(b[i]))
    return res == 0

def crypto_box_open(request, boxed_blob):
    # Check the hmac first
    hmac_secret = request.registry.settings["session.validate_key"]
    mac = hmac.new(hmac_secret, None, hashlib.sha1)
    if len(boxed_blob) < mac.digest_size:
        raise ValueError("boxed blob was too short to hold an HMAC")
    # boxed_blob is ciphertext:mac
    box_contents = boxed_blob[:-mac.digest_size]
    expected_digest = boxed_blob[-mac.digest_size:]
    mac.update(box_contents)
    actual_digest = mac.digest()
    if not _constant_time_is_equal(expected_digest, actual_digest):
        raise ValueError("boxed blob's MAC was invalid")
    # Then decrypt the boxed blob
    # box_contents is iv:ciphertext
    aes_key = PBKDF2(request.registry.settings["session.encrypt_key"], "two_factor_salt", dkLen=16)
    iv = box_contents[:AES.block_size]
    ciphertext = box_contents[AES.block_size:]
    cipher = AES.new(aes_key, AES.MODE_CBC, iv)
    plaintext = cipher.decrypt(ciphertext)
    return _pkcs7_unpad(plaintext)

