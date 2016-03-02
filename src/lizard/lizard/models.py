import datetime
import flask_scrypt as scrypt
from lizard import db
from flask import current_app


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

# The length of a randomly-generated signup/promo code (should be 128 bits of randomness)
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

    # Whether the company has accepted our license agreement.  If not, we
    # shouldn't let them request a license nor download a license.
    accepted_license = db.Column(db.Boolean, default=False, nullable=False)

    # Stripe Customer ID used for billing
    stripe_customer_id = db.Column(db.String(_USER_STRING_MAX_LEN))

    # A customer has a list of admins who are allowed to manage things about
    # the organization, request licenses, accept the license agreement, and pay
    # the bills.
    admins = db.relationship("Admin", backref="customer", lazy="dynamic")

    # A customer may have a list of emails that have been invited to become
    # admins, but have not yet been verified yet.
    pending_invites = db.relationship("BoundInvite", backref="customer", lazy="dynamic")

    # A customer may have many licenses and license requests in the pipeline.
    licenses = db.relationship("License", backref="customer", lazy="dynamic")

    # A customer may have at most one 'active', newest appliance
    appliances = db.relationship("Appliance", backref="customer", lazy="dynamic")

    # A customer may have zero to many Hosted Private Cloud Appliances
    hpc_appliances = db.relationship("HPCDeployment", backref="customer", lazy="dynamic")

    # A customer may have zero to many registered mail domains
    domains = db.relationship("Domain", backref="customer", lazy="dynamic")

    # How many seats should the customer be billed for on Renewal?
    # This is used at the time of renewal.
    renewal_seats = db.Column(db.Integer, nullable=True)

    # Return the most recently updated Filled license. No sorting on trial/paid here.
    def newest_filled_license(self):
        return self.licenses.filter_by(state=License.states.FILLED).order_by(
                License.modify_date.desc()
            ).first()

    # Return the most interesting license request for billing / purchase requests.
    # If there is a Pending license, definitely return it.
    # Otherwise, among Filled licenses, return the most recently updated.
    # If there are none of those, return a Held or Ignored license request.
    # NOTE that the newest_license is not necessarily an "active", usable license.
    def newest_license(self):
        active_license = self.licenses.filter_by(state=License.states.FILLED).order_by(
                    License.modify_date.desc(),
                    License.expiry_date.desc(),
                ).first()

        pending_license = self.licenses.filter_by(state=License.states.PENDING).order_by(
                    License.modify_date.desc(),
                    License.expiry_date.desc(),
                ).first()

        held_license = self.licenses.filter_by(state=License.states.ON_HOLD).order_by(
                    License.modify_date.desc(),
                    License.expiry_date.desc(),
                ).first()

        ignored_license = self.licenses.filter_by(state=License.states.IGNORED).order_by(
                    License.modify_date.desc(),
                    License.expiry_date.desc(),
                ).first()

        return pending_license or active_license or held_license or ignored_license

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
    promo_code = db.Column(db.String(_SIGNUP_CODE_LENGTH), nullable=True)

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

    def set_password(self, password):
        # Generate a new random salt every time we set the password.
        salt = scrypt.generate_random_salt()
        self.salt = salt
        # scrypt her password with that salt
        self.pw_hash = scrypt.generate_password_hash(password, salt)

    def __repr__(self):
        return "<Admin {}>".format(self.email)

    # The following four methods are used for Flask-Login integration:
    def is_active(self):
        # Required for Flask-Login integration.
        # Returns True if this user and this user's owning Customer are both
        # active, False otherwise
        return self.active

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

class License(db.Model, TimeStampedMixin):
    __tablename__ = "license"

    # state: Which queue is this license in?
    # PENDING: will be in the next license request bundle
    # ON_HOLD: will not be in the next license request bundle, waiting for user to justify request
    # FILLED:  License has been issued and the binary blob sits in LicenseIssued.
    #          You can find a LicenseIssued with license_request_id matching this id.
    # IGNORED: This request is being permanently ignored.
    # DO NOT REORDER THESE STATES.  THEY ARE AN ENUMERATION AND STORED IN THE DB.
    class LicenseState(object):
        PENDING = 0
        ON_HOLD = 1
        FILLED  = 2
        IGNORED = 3
        states = {
                   0: "PENDING",
                   1: "ON_HOLD",
                   2: "FILLED",
                   3: "IGNORED",
                   }
    states = LicenseState()

    # This id should be randomized to make sure that we don't leak license
    # issue counts through the license id.
    id = db.Column(db.Integer, primary_key=True)

    # Which customer is this license for?
    customer_id = db.Column(db.Integer, db.ForeignKey('customer.id'), nullable=False)

    # Is this license waiting to be issued?  issued already?  on hold for some reason? etc.
    state = db.Column(db.Integer, default=states.PENDING, nullable=False)

    # License parameters.
    seats = db.Column(db.Integer, nullable=False)

    expiry_date = db.Column(db.DateTime, nullable=False)

    is_trial = db.Column(db.Boolean, nullable=False)
    allow_audit = db.Column(db.Boolean, default=False, nullable=False)
    allow_identity = db.Column(db.Boolean, default=False, nullable=False)
    allow_mdm = db.Column(db.Boolean, default=False, nullable=False)
    allow_device_restriction = db.Column(db.Boolean, default=False, nullable=False)

    # Each license should generally have either a stripe subscription id or an invoice ID
    # Exceptions are for trial licenses, and well, when we're feeling nice...
    # Stripe Subscription ID
    stripe_subscription_id = db.Column(db.String(256), nullable=True)
    # Invoice ID
    invoice_id = db.Column(db.String(256), nullable=True)

    # The license data itself.  Null unless state == FILLED.
    blob = db.Column(db.LargeBinary)

    def set_days_until_expiry(self, d):
        e = datetime.datetime.today().date() + datetime.timedelta(days=d)
        self.expiry_date = datetime.datetime(year=e.year, month=e.month, day=e.day)


class Appliance(db.Model, TimeStampedMixin):
    __tablename__ = "appliance"

    id = db.Column(db.Integer, primary_key=True)

    # To which customer does this appliance record belong?
    customer_id = db.Column(db.Integer, db.ForeignKey('customer.id'), nullable=False)

    # Hostname, as entered by the admin. We can't server-side validate this address.
    hostname = db.Column(db.String(256), nullable=False)

    def __repr__(self):
        return "<Appliance '{}'>".format(self.hostname)

class Domain(db.Model, TimeStampedMixin):
    __tablename__ = "domain"

    id = db.Column(db.Integer, primary_key=True)

    # To which customer does this mail domain belong?
    customer_id = db.Column(db.Integer, db.ForeignKey('customer.id'), nullable=False)

    # Mail domain as entered by an admin
    mail_domain = db.Column(db.String(256), nullable=False)

    # Date that this domain was validated by (...)
    verify_date = db.Column(db.DateTime, nullable=False)

    def __repr__(self):
        return "<Domain '{}' '{}'>".format(self.mail_domain, self.verify_date is not None)


class HPCDeployment(db.Model, TimeStampedMixin):
    __tablename__ = "hpc_deployment"

    # Subdomain on which this deployment is available
    # ie: <subdomain>.aerofs.com
    # Letters, numbers and dashes only. Can't start or end with a dash.
    subdomain = db.Column(db.String(32), primary_key=True)

    # To which customer does this deployment belong?
    customer_id = db.Column(db.Integer, db.ForeignKey('customer.id'), nullable=False)

    # On which server is this running?
    server_id = db.Column(db.Integer, db.ForeignKey('hpc_server.id'), nullable=False)

    # When the appliance was set up
    appliance_setup_date = db.Column(db.DateTime, nullable=True)

    # Returns the full host name for this deployment. E.g.: 'foobar.aerofs.com'
    def full_hostname(self):
        return '{}.{}'.format(self.subdomain, current_app.config['HPC_DOMAIN'])

    # we are talking here about the expiry of the license
    def set_days_until_expiry(self, d):
        license = self.customer.newest_filled_license()
        license.set_days_until_expiry(d)

    def get_days_until_expiry(self):
        today = datetime.datetime.today().date()
        start_of_today = datetime.datetime(year=today.year, month=today.month, day=today.day)
        # In case there are no filled licenses
        try:
            days = (self.customer.newest_filled_license().expiry_date - start_of_today).days
        except AttributeError:
            return "No filled licenses"

        return -1 if days < 0 else days

    def has_expired(self):
        today = datetime.datetime.today().date()
        start_of_today = datetime.datetime(year=today.year, month=today.month, day=today.day)
        return start_of_today >= self.customer.newest_filled_license().expiry_date

    def __repr__(self):
        return "<HPCDeployment '{}' expires: '{}'>".format(self.subdomain, self.customer.newest_filled_license.strftime('%Y-%m-%d'))


class HPCServer(db.Model, TimeStampedMixin):
    __tablename__ = "hpc_server"

    id = db.Column(db.Integer, primary_key=True)

    # URL of the Docker daemon HTTPS REST API on this server
    # Typically this would be in the form https://<amazon_vpc_ip_address>:2376
    docker_url = db.Column(db.String(256))

    # Public IP address of this server
    # This is what the DNS type A record will be set to for the deployment's subdomain
    public_ip = db.Column(db.String(15))

    # A server may have zero to many deployments on it
    deployments = db.relationship("HPCDeployment", backref="server")

    def __repr__(self):
        return "<HPCServer '{}'>".format(self.docker_url)

# Each class with create_date/modify_date autoupdate magic must register here
Customer.register()
Admin.register()
License.register()
Appliance.register()
Domain.register()
HPCDeployment.register()
HPCServer.register()