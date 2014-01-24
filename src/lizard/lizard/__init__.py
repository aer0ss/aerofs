import os
import logging

from flask import Flask
from flask.ext.login import LoginManager
from flask.ext.sqlalchemy import SQLAlchemy
from flask_wtf.csrf import CsrfProtect
from migrate.versioning import api
from migrate.exceptions import DatabaseAlreadyControlledError

from .flask_analytics import AnalyticsClient

# Login manager.  Flask-login does session management.
login_manager = LoginManager()

# CSRF protection from WTForms
csrf = CsrfProtect()

# Database stuff
db = SQLAlchemy()

def migrate_database(app):
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
analytics_client = AnalyticsClient()

from lizard import views

def create_app():
    app = Flask(__name__)
    # Base configuration.
    app.config.from_object('config')
    # Deployment-specific configuration.
    app.config.from_object('additional_config')

    # Enable plugins:
    # 1) Flask-Login
    login_manager.init_app(app)
    login_manager.login_view = 'login_page'

    # 2) Flask-WTF CSRF protection
    csrf.init_app(app)

    # 3) Flask-SQLAlchemy
    db.init_app(app)
    _moddir = os.path.abspath(os.path.dirname(__file__))
    app.config['SQLALCHEMY_MIGRATE_REPO'] = os.path.join(_moddir, 'migrations')
    print app.config

    # 4) SegmentIO
    analytics_client.init_app(app)

    # Enable routes
    app.register_blueprint(views.blueprint, url_prefix="")

    # Return configured app
    return app
