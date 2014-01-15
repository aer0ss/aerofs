import os
import logging

from flask import Flask
from flask.ext.login import LoginManager
from flask.ext.sqlalchemy import SQLAlchemy
from flask_wtf.csrf import CsrfProtect
from migrate.versioning import api
from migrate.exceptions import DatabaseAlreadyControlledError
import analytics.client

app = Flask(__name__)
# Base configuration.
app.config.from_object('config')
# Deployment-specific configuration.
app.config.from_object('additional_config')

# Login manager.  Flask-login does session management.
login_manager = LoginManager()
login_manager.init_app(app)
login_manager.login_view = 'login_page'

# CSRF protection from WTForms
csrf = CsrfProtect()
csrf.init_app(app)

# Database stuff
db = SQLAlchemy(app)
_moddir = os.path.abspath(os.path.dirname(__file__))
app.config['SQLALCHEMY_MIGRATE_REPO'] = os.path.join(_moddir, 'migrations')

def migrate_database():
    db_uri = app.config['SQLALCHEMY_DATABASE_URI']
    repo = app.config['SQLALCHEMY_MIGRATE_REPO']
    # If the DB isn't under version control yet, add the migrate_version table
    # at version 0
    try:
        api.version_control(db_uri, repo)
    except DatabaseAlreadyControlledError:
        # we already own the DB
        pass

    # Apply all known migrations to bring the database schema up to date
    api.upgrade(db_uri, repo, api.version(repo))

# Analytics stuff
segmentio_enabled = "SEGMENTIO_SECRET_KEY" in app.config
surrogate_key = app.config["SEGMENTIO_SECRET_KEY"] if segmentio_enabled else ""
analytics_client = analytics.client.Client(secret=surrogate_key,
                                           flush_at=1,
                                           log=True,
                                           log_level=logging.DEBUG,
                                           async=False,
                                           send=segmentio_enabled)
print app.config

from lizard import emails, forms, models, views
