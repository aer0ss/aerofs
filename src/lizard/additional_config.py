# Note: this file is for testing configuration only.  Make sure to update
# production configuration in
# puppetmaster/modules/lizard/files/additional_config.py
# if you add any required keys.
DEBUG=True
# These segmentio api and secret keys are for testing only.
SEGMENTIO_WRITE_KEY="DXiiCHhPnCb0IhkSy0BcGSe1UQBCAbNS"
SEGMENTIO_DEBUG=True

# These stripe keys are for testing only
STRIPE_SECRET_KEY="sk_test_vEjpSRt2LE4jgfxB709l8NCG"
STRIPE_PUBLISHABLE_KEY="pk_test_LL4hvnijboGKs7CJLA6CUh15"
# local dev: use sqlite db
import os
basedir = os.path.abspath(os.path.dirname(__file__))
SQLALCHEMY_DATABASE_URI = 'sqlite:///' + os.path.join(basedir, 'state', 'database.db')

# Email: in development use svmail
# In production, prefer sendgrid
MAIL_SERVER = "devmail.aerofs.com"
MAIL_PORT = 25
MAIL_USE_TLS = False
MAIL_USE_SSL = False
MAIL_DEBUG = False
MAIL_USERNAME = None
MAIL_PASSWORD = None
