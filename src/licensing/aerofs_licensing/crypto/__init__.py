#!/usr/bin/env python
from signer import sign_with_aerofs_licensing_privkey
from verifier import verify_with_aerofs_licensing_pubkey

# public API for crypto functions
def sign(input_flo, output_flo):
    """
    Signs the data read from python file-like-object `input_flo`, outputs the
    OpenPGP-compatible signature data to `output_flo`, and returns a list
    containing any new signatures added.
    """
    return sign_with_aerofs_licensing_privkey(input_flo, output_flo)

def verify(input_flo, output_flo):
    """
    Verifies the data read from python file-like-object `input_flo`, outputs the
    signed bytes to `output_flo`, and returns a list of the signatures present.
    """
    return verify_with_aerofs_licensing_pubkey(input_flo, output_flo)

