from lizard import db

import datetime

# Maximum length of a valid email address, in bytes, including null terminator
# "restricts the entire email address to be no more than 254 characters"
# http://tools.ietf.org/html/rfc5321#section-4.5.3.1
_EMAIL_MAX_LEN = 256

# Arbitrary limit for user-provided strings indended to bound resource usage
# from user strings
_USER_STRING_MAX_LEN = 256

# The size of 64 bytes of data base64'd, and then given a null terminator
# len(base64(' ' * 32)) + 1
_SALT_LENGTH = 89
_HASH_LENGTH = 89

# The length of a randomly-generated signup code (should be 128 bits of randomness)
# len(base64(' ' * 32)) + 1
_SIGNUP_CODE_LENGTH = 45

class TimeStampedMixin(object):
    # A semi-magical mixin class that provides two columns for tracking
    # creation date and modification date of rows.  It works by registering a
    # couple of functions to be called just before INSERT or UPDATE queries are
    # made to the backend DB.

    # To use this, simply subclass Model and TimeStampedMixin, and then call
    # YourModel.register() to get auto-updating for all instances of the class.

    # Standard create/update timestamps for recordkeeping
    # All timestamps should be stored in UTC.
    create_date = db.Column(db.DateTime, nullable=False)
    modify_date = db.Column(db.DateTime, nullable=False)
    @staticmethod
    def create_time(mapper, connection, instance):
        now = datetime.datetime.utcnow()
        instance.create_date = now
        instance.modify_date = now

    @staticmethod
    def update_time(mapper, connection, instance):
        now = datetime.datetime.utcnow()
        instance.modify_date = now

    @classmethod
    def register(cls):
        db.event.listen(cls, 'before_insert', cls.create_time)
        db.event.listen(cls, 'before_update', cls.update_time)

class Customer(db.Model, TimeStampedMixin):
    # Customer is a model that represents a company

    id = db.Column(db.Integer, primary_key=True)

    # Company name, e.g. "Air Computing, Inc."
    name = db.Column(db.String(_USER_STRING_MAX_LEN), nullable=False)

    # Whether the company is "active" - I had this in my drawing, but I'm not
    # sure what this means yet.  Maybe we don't send emails if this is removed?
    active = db.Column(db.Boolean, default=True, nullable=False)

    # Whether the company has accepted our license agreement.  If not, we
    # shouldn't let them request a license nor download a license.
    accepted_license = db.Column(db.Boolean, default=False, nullable=False)

    # These can come later, I guess
    #billing_info = ?
    #billing_history = ?

    # A customer has a list of admins who are allowed to manage things about
    # the organization, request licenses, accept the license agreement, and pay
    # the bills.
    admins = db.relationship("Admin", backref="customer", lazy="dynamic")

    # A customer may have a list of emails that have been invited to become
    # admins, but have not yet been verified yet.
    pending_invites = db.relationship("BoundInvite", backref="customer", lazy="dynamic")

    def __repr__(self):
        return "<Customer '{}'>".format(self.name)

class UnboundSignup(db.Model):
    # Email verification for new company signups.
    # Because we have not created the company yet, we do not ask for email address.
    id = db.Column(db.Integer, primary_key=True, autoincrement=True)
    signup_code = db.Column(db.String(_SIGNUP_CODE_LENGTH), index=True, unique=True, nullable=False)
    email = db.Column(db.Unicode(length=_EMAIL_MAX_LEN), index=True, nullable=False)
    first_name = db.Column(db.String(_USER_STRING_MAX_LEN), nullable=False)
    last_name = db.Column(db.String(_USER_STRING_MAX_LEN), nullable=False)
    company_name = db.Column(db.String(_USER_STRING_MAX_LEN), nullable=False)
    # We allow these to be null or empty
    phone_number = db.Column(db.String(_USER_STRING_MAX_LEN), nullable=True)
    job_title = db.Column(db.String(_USER_STRING_MAX_LEN), nullable=True)

class BoundInvite(db.Model):
    # A model that represents an email address that a customer has specified
    # should be added to the list of admins, but the email hasn't been verified
    # yet, so they're not a proper user yet.
    id = db.Column(db.Integer, primary_key=True, autoincrement=True)
    # This is the random string that we use to verify email address ownership
    invite_code = db.Column(db.String(_SIGNUP_CODE_LENGTH), index=True, unique=True, nullable=False)
    email = db.Column(db.Unicode(length=_EMAIL_MAX_LEN), index=True, nullable=False)
    customer_id = db.Column(db.Integer, db.ForeignKey('customer.id'), nullable=False)

class Admin(db.Model, TimeStampedMixin):
    # An Admin represents a user that can log in with an email address and
    # password, belongs to a particular customer, and has preferences.
    id = db.Column(db.Integer, primary_key=True, autoincrement=True)

    # Required accounting fields
    email = db.Column(db.Unicode(length=_EMAIL_MAX_LEN), index=True, unique=True, nullable=False)
    customer_id = db.Column(db.Integer, db.ForeignKey('customer.id'), nullable=False)
    first_name = db.Column(db.String(_USER_STRING_MAX_LEN), nullable=False)
    last_name = db.Column(db.String(_USER_STRING_MAX_LEN), nullable=False)

    # Optional
    phone_number = db.Column(db.String(_USER_STRING_MAX_LEN), nullable=True)
    job_title = db.Column(db.String(_USER_STRING_MAX_LEN), nullable=True)

    # Login credentials (per-user random salt, hash)
    # scrypt ftw
    salt = db.Column(db.String(_SALT_LENGTH), nullable=False)
    pw_hash = db.Column(db.String(_HASH_LENGTH), nullable=False)

    # active - set false to disable login for a particular user without deleting their account.
    active = db.Column(db.Boolean, default=True, nullable=False)

    # Notification preferences.  Should this admin be emailed for:
    # 1. Security-related messages?
    # 2. New image releases?
    # 3. Maintenance-related messages?
    notify_security = db.Column(db.Boolean, default=True, nullable=False)
    notify_release = db.Column(db.Boolean, default=True, nullable=False)
    notify_maintenance = db.Column(db.Boolean, default=True, nullable=False)

    def __repr__(self):
        return "<Admin {}>".format(self.email)

    # The following four methods are used for Flask-Login integration:
    def is_active(self):
        # Required for Flask-Login integration.
        # Returns True if this user and this user's owning Customer are both
        # active, False otherwise
        return self.active and self.customer.active

    def is_authenticated(self):
        # Required for Flask-Login integration.
        # By the time you've received one of these, you already have a valid
        # user token.  Users are authenticated
        return True

    def is_anonymous(self):
        # Required for Flask-Login integration.
        return False

    def get_id(self):
        # Required for Flask-Login integration.
        return self.email

# Each class with create_date/modify_date autoupdate magic must register here
Customer.register()
Admin.register()
