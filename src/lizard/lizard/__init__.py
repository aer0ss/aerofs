import os

from flask import Flask
from flask_login import LoginManager
from flask_sqlalchemy import SQLAlchemy
from flask_wtf.csrf import CsrfProtect
from migrate.versioning import api
from migrate.exceptions import DatabaseAlreadyControlledError
from celery import Celery
from celery.schedules import crontab
from .flask_analytics import AnalyticsClient

import stripe
import boto3

# Login manager.  Flask-login does session management.
login_manager = LoginManager()

# CSRF protection from WTForms
csrf = CsrfProtect()

# Database stuff
db = SQLAlchemy()

celery = Celery(__name__, broker='redis://', backend='redis://')
celery.conf.update(
    CELERY_TASK_SERIALIZER='json',
    CELERY_ACCEPT_CONTENT=['json'],
    CELERY_RESULT_SERIALIZER='json',
    CELERYD_CONCURRENCY=6,
    CELERY_ACKS_LATE=True,
    CELERYBEAT_SCHEDULE = {
        'every-night': {
            'task': 'lizard.hpc.check_expired_deployments',
            'schedule': crontab(minute=0, hour=0),
            'args': (),
        },
        'sqs-notifications': {
            'task': 'lizard.hpc.check_sqs_notifications',
            'schedule': crontab(minute='*/15'),
            'args': (),
        }
    }
    # TODO: Enable Celery error emails so that we get alerts when tasks fail
    # See: http://docs.celeryproject.org/en/latest/configuration.html#error-e-mails
)


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

from lizard import views, internal_views, filters

def create_app(internal=False):
    app = Flask(__name__)
    # Base configuration.
    app.config.from_object('config')
    # Deployment-specific configuration.
    app.config.from_object('additional_config')

    app.jinja_env.filters['timestamp_to_date'] = filters.timestamp_to_datetime
    app.jinja_env.filters['format_currency'] = filters.format_currency
    app.jinja_env.filters['date'] = filters.date

    # Enable plugins:
    # 1) Flask-Login
    login_manager.init_app(app)
    login_manager.login_view = '.login_page'

    # 2) Flask-WTF CSRF protection
    csrf.init_app(app)

    # 3) Flask-SQLAlchemy
    db.init_app(app)
    _moddir = os.path.abspath(os.path.dirname(__file__))
    app.config['SQLALCHEMY_MIGRATE_REPO'] = os.path.join(_moddir, 'migrations')
    print app.config

    # 4) SegmentIO
    analytics_client.init_app(app)

    # 5) Stripe
    stripe.api_key = app.config['STRIPE_SECRET_KEY']

    # 6) AWS (Route 53)
    aws_session = boto3.session.Session(aws_access_key_id=app.config['HPC_AWS_ACCESS_KEY'],
                                        aws_secret_access_key=app.config['HPC_AWS_SECRET_KEY'],
                                        region_name='us-east-1')
    app.route53 = aws_session.client('route53')
    app.s3 = aws_session.resource('s3')
    app.autoscaling = aws_session.client('autoscaling')
    app.ec2 = aws_session.resource('ec2')
    app.sqs_resource = aws_session.resource('sqs')
    app.sqs_client = aws_session.client('sqs')

    # Enable routes
    if internal:
        app.register_blueprint(internal_views.blueprint, url_prefix="")
    else:
        app.register_blueprint(views.blueprint, url_prefix="")

    # Return configured app
    return app
