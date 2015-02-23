from lib.app.install import get_installer
from syncdet.case import user_specified_args

import argparse

# this case accepts one optional arg "transport" which can be specified as a
# "case-arg" when invoking syncdet.py
# e.g. ./syncdet.py --case=clean_install --case-arg=--transport=zephyr the
# above will add '--transport=zephyr' to user_specified_args, which can be
# parsed with argparse as below


def clean_install():
    parser = argparse.ArgumentParser()
    parser.add_argument('--transport', default=None)
    args = parser.parse_args(user_specified_args())

    installer = get_installer()
    installer.install(transport=args.transport, clean_install=True)


spec = {
    'default': clean_install,
    'timeout': 5 * 60
}
