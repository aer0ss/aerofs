import os
basedir = os.path.abspath(os.path.dirname(__file__))

# This URI is only good for testing
SQLALCHEMY_DATABASE_URI = 'sqlite:///' + os.path.join(basedir, 'state', 'database.db')
