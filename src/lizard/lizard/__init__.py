import os

from flask import Flask
from flask.ext.sqlalchemy import SQLAlchemy
from migrate.versioning import api
from migrate.exceptions import DatabaseAlreadyControlledError

app = Flask(__name__)
app.config.from_object('config')
# Also load overrides from a file referred to by an environment variable, if
# present.  Allows for dev settings to override production ones for local
# development.
if 'CONFIG_EXTRA' in os.environ:
    app.config.from_envvar('CONFIG_EXTRA')

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

from lizard import models, views
