#!/usr/bin/env python
from signer import sign_with_aerofs_licensing_privkey
from verifier import verify_with_aerofs_licensing_pubkey

# public API for crypto functions
def sign(input_flo, output_flo, gpg_homedir=None, password_cb=None):
    """
    Signs the data read from python file-like-object `input_flo`, outputs the
    OpenPGP-compatible signature data to `output_flo`, and returns a list
    containing any new signatures added.
    """
    return sign_with_aerofs_licensing_privkey(input_flo, output_flo, gpg_homedir, password_cb)

def verify(input_flo, output_flo, gpg_homedir=None):
    """
    Verifies the data read from python file-like-object `input_flo`, outputs the
    signed bytes to `output_flo`, and returns a list of the signatures present.
    """
    return verify_with_aerofs_licensing_pubkey(input_flo, output_flo, gpg_homedir)
