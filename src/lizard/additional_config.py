import os

# Note: this file is for testing configuration only. Make sure to update production configuration in:
# ~/repos/aerofs/puppetmaster/modules/lizard/templates/additional_config.py.erb
# if you add any required keys.
DEBUG=True

# These segmentio api and secret keys are for testing only.
SEGMENTIO_WRITE_KEY="DXiiCHhPnCb0IhkSy0BcGSe1UQBCAbNS"
SEGMENTIO_DEBUG=True

# These stripe keys are for testing only
STRIPE_SECRET_KEY="sk_test_vEjpSRt2LE4jgfxB709l8NCG"
STRIPE_PUBLISHABLE_KEY="pk_test_LL4hvnijboGKs7CJLA6CUh15"

# local mysql. Make sure to `CREATE DATABASE lizard;`
SQLALCHEMY_DATABASE_URI = 'mysql+oursql://root@localhost/lizard?raise_on_warnings=False'
# Email: in development use svmail
# In production, prefer sendgrid
MAIL_SERVER = "devmail.aerofs.com"
MAIL_PORT = 25
MAIL_USE_TLS = False
MAIL_USE_SSL = False
MAIL_DEBUG = False
MAIL_USERNAME = None
MAIL_PASSWORD = None

#################################
# Hosted Private Cloud settings
#################################

# Credentials for user 'hosted_private_cloud_test' on Amazon AWS.
# This user can do only one thing: edit Route 53 subdomains for "syncfs.com".
# https://console.aws.amazon.com/iam/home?#users/hosted_private_cloud_test
HPC_AWS_ACCESS_KEY = 'AKIAJMRKGMRSXXUS6NLQ'
HPC_AWS_SECRET_KEY = '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'

# The Route 53 Hosted Zone on which we will create HPC subdomains.
HPC_ROUTE_53_HOSTED_ZONE_ID = 'Z2COTO8BJU2Z9X'  # syncfs.com - https://console.aws.amazon.com/route53/home#resource-record-sets:Z2COTO8BJU2Z9X

# The domain name that we will use for Hosted Private Cloud.
# This domain must match HPC_ROUTE_53_HOSTED_ZONE_ID
HPC_DOMAIN = 'syncfs.com'

# Certificates used to communicate with the Docker daemon
HPC_PATH_TO_DOCKER_CA_CERT = os.path.expanduser('~/.docker/machine/certs/ca.pem')
HPC_PATH_TO_DOCKER_CLIENT_CERT = os.path.expanduser('~/.docker/machine/certs/cert.pem')
HPC_PATH_TO_DOCKER_CLIENT_KEY = os.path.expanduser('~/.docker/machine/certs/key.pem')
