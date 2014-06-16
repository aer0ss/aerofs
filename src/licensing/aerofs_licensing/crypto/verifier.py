#!/usr/bin/env python
import gpgme
from util import find_gpg_executable, get_licensing_key, gpg_public_homedir

def verify_with_aerofs_licensing_pubkey(input_flo, output_flo, gpg_homedir=None):
    homedir = gpg_homedir or gpg_public_homedir
    # Use the keys available in the specified homedir
    ctx = gpgme.Context()
    ctx.set_engine_info(gpgme.PROTOCOL_OpenPGP, find_gpg_executable(), homedir)

    # Set the key to be used for verification
    licensing_key = get_licensing_key(ctx)
    if not licensing_key:
        raise IOError("Couldn't find verifier key - is the gpg homedir present?")
    ctx.signers = [ licensing_key ]

    # Perform the verify
    sigs = ctx.verify(input_flo, None, output_flo)
    # Check that the fpr of the signature matches that of one of the subkeys of the licensing key
    trusted_fprs = [ subkey.fpr for subkey in licensing_key.subkeys ]
    trustworthy = any([ sig.fpr in trusted_fprs for sig in sigs ])
    if not trustworthy:
        raise ValueError("Not trusted by any trustworthy keys")
    #print "signed by {}".format(sigs[0].fpr)
    return sigs

