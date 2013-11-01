#!/usr/bin/env python
import getpass
import gpgme
import os
from util import find_gpg_executable, get_licensing_key, gpg_secret_homedir

def _passphrase_cb(uid_hint, passphrase_info, prev_was_bad, fd):
    """
    This function is the callback called by the gpgme engine when it needs a
    passphrase to decrypt a private key (to be used for signing).  It is
    expected to write the passphrase, followed by a newline, to the file
    descriptor `fd`.

    For additional information, see:
    http://www.gnupg.org/documentation/manuals/gpgme/Passphrase-Callback.html
    """
    if prev_was_bad:
        print "Passphrase was rejected."
    print "Enter passphrase for {}".format(uid_hint)
    passphrase = getpass.getpass() + "\n"
    os.write(fd, passphrase)

def sign_with_aerofs_licensing_privkey(input_flo, output_flo, gpg_homedir=None, passphrase_cb=None):
    # handle default args
    homedir = gpg_homedir or gpg_secret_homedir
    callback = passphrase_cb or _passphrase_cb

    # Use the keys available in the specified homedir
    ctx = gpgme.Context()
    ctx.set_engine_info(gpgme.PROTOCOL_OpenPGP, find_gpg_executable(), gpg_homedir)

    # Set the key to be used for signing
    licensing_privkey = get_licensing_key(ctx)
    if not licensing_privkey:
        raise IOError("Couldn't find the license key - is the gpg homedir present?")
    ctx.signers = [ licensing_privkey ]
    ctx.passphrase_cb = callback

    # Perform the actual signing
    new_sigs = ctx.sign(input_flo, output_flo, gpgme.SIG_MODE_NORMAL)
    # TODO: verify that new_sigs contains a sig for the right key
    #print "signed by {}".format(new_sigs[0].fpr)
    return new_sigs
