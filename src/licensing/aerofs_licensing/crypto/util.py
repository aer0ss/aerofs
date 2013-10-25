#!/usr/bin/env python
import os

_pwd = os.path.dirname( os.path.realpath( __file__ ) )
gpg_secret_homedir = os.path.join(_pwd, "license_root_key")
gpg_public_homedir = os.path.join(_pwd, "license_root_public")

def find_gpg_executable():
    paths = os.environ["PATH"].split(os.path.pathsep)
    for p in paths:
        possible_path = os.path.join(p, "gpg")
        if os.path.isfile(possible_path):
            return possible_path
    raise IOError("Couldn't find gpg executable")

def get_licensing_key(ctx):
    """
    Returns the first key matching `licensing@aerofs.com` from the gpgme
    context passed in as `ctx`, or raises IOError if no such key is known
    that that context.
    """
    for k in ctx.keylist():
        for uid in k.uids:
            if uid.email == "licensing@aerofs.com":
                return k
    raise ValueError("Couldn't find AeroFS license key")
