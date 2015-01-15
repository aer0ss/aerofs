# Note: this file is for testing configuration only.  Make sure to update
# production configuration in
# puppetmaster/modules/lizard/files/additional_config.py
# if you add any required keys.
DEBUG=True
# These segmentio api and secret keys are for testing only.
SEGMENTIO_API_KEY="d66ddysfvg"
SEGMENTIO_SECRET_KEY="j85f4itjg9bxc6ogyh49"

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
