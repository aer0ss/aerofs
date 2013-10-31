#!/usr/bin/env python
import gpgme
from util import find_gpg_executable, get_licensing_key, gpg_public_homedir
from io import BytesIO

def verify_with_aerofs_licensing_pubkey(input_flo, output_flo):
    # Use the keys available in the specified homedir
    ctx = gpgme.Context()
    ctx.set_engine_info(gpgme.PROTOCOL_OpenPGP, find_gpg_executable(), gpg_public_homedir)

    # Set the key to be used for verification
    licensing_privkey = get_licensing_key(ctx)
    if not licensing_privkey:
        raise IOError("Couldn't find verifier key - is the gpg homedir present?")
    ctx.signers = [ licensing_privkey ]

    # Perform the verify
    sigs = ctx.verify(input_flo, None, output_flo)
    # TODO: verify that our key is in the list of sigs for this file
    print "signed by {}".format(sigs[0].fpr)
    return sigs

