import os
basedir = os.path.abspath(os.path.dirname(__file__))

# Stuff for CSRF tokens & login
CSRF_ENABLED = True
csrf_keyfile = os.path.join(basedir, 'state', 'csrf_secret')
if not os.path.exists(csrf_keyfile):
    with open("/dev/urandom") as rng:
        key = rng.read(64)
    with open(csrf_keyfile, "w") as f:
        f.write(key)

with open(csrf_keyfile) as f:
    SECRET_KEY = f.read()

RELEASE_VERSION_FILE = os.path.join(basedir, 'state', 'release_version')

# This URI is only good for testing
SQLALCHEMY_DATABASE_URI = 'sqlite:///' + os.path.join(basedir, 'state', 'database.db')
