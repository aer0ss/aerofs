import datetime

from flask.ext.wtf import Form
from flask_wtf.file import FileField, FileRequired
from wtforms import TextField, PasswordField, BooleanField, \
        IntegerField, DateField, SelectField, TextAreaField
from wtforms.validators import ValidationError, InputRequired, Email, Length, Optional, EqualTo

class LoginForm(Form):
    email = TextField('Email', validators=[Email()])
    password = PasswordField('Password')

class SignupForm(Form):
    first_name = TextField('First Name', validators=[InputRequired()])
    last_name = TextField('Last Name', validators=[InputRequired()])
    email = TextField('Email', validators = [InputRequired(), Email()])
    company_name = TextField('Organization Name', validators=[InputRequired()])
    phone_number = TextField("Phone Number", validators=[InputRequired()])
    job_title = TextField("Job Title", validators=[Optional()])

class CompleteSignupForm(Form):
    password = PasswordField('Password', validators=[Length(min=6)])

class AcceptInviteForm(Form):
    first_name = TextField('First Name', validators=[InputRequired()])
    last_name = TextField('Last Name', validators=[InputRequired()])
    password = PasswordField('Password', validators=[Length(min=6)])
    phone_number = TextField("Phone Number", validators=[Optional()])
    job_title = TextField("Job Title", validators=[Optional()])

class InviteForm(Form):
    email = TextField('Email address', validators = [Email()])

class PreferencesForm(Form):
    first_name = TextField('First Name', validators=[InputRequired()])
    last_name = TextField('Last Name', validators=[InputRequired()])
    password = PasswordField("Password", validators=[
        Optional(),
        Length(min=6),
        EqualTo('password_confirmation', message='Passwords must match.')
        ])
    password_confirmation = PasswordField("Password confirmation")
    # DF: these fields disabled until we figure out our story with email notifications
    #security_emails = BooleanField("Receive security notifications")
    #release_emails = BooleanField("Receive release notifications")
    #maintenance_emails = BooleanField("Receive maintenance notifications")

class LicenseCountForm(Form):

    count = SelectField('New License Count', choices=[
        ('10','10'),
        ('25', '25'),
        ('50', '50'),
        ('100', '100'),
        ('250', '250'),
        ('500', '500'),
        ('1000', '1000')
        ])

def IsFutureDate(message=None):
    def _IsFutureDate(form, field):
        if field.data < datetime.datetime.today().date():
            raise ValidationError(u"Date must be in the future")
    return _IsFutureDate

class InternalLicenseRequestForm(Form):
    seats = IntegerField("Seats", validators=[InputRequired()])
    expiry_date = DateField("Expiry Date (YYYY-MM-DD)", validators=[InputRequired(), IsFutureDate() ])
    is_trial = BooleanField("Trial?")
    allow_audit = BooleanField("Allow Audit?")
    manual_invoice = TextField("Manual Invoice ID?(Required for manual license requests)", validators=[Optional()])
class InternalLicenseBundleUploadForm(Form):
    license_bundle = FileField("License bundle:", validators=[FileRequired()])

class InternalLicenseStateForm(Form):
    # Note: _licensehelper.html has some macros that depend on the name of this field
    state = SelectField(u'Queue', choices=[
        ('PENDING', 'PENDING'),
        ('ON_HOLD', 'ON HOLD'),
        ('IGNORED', 'IGNORED')
    ])
    invoice_id = TextField("Manual Invoice ID", validators=[Optional()])
    stripe_id = TextField("Stripe Charge ID", validators=[Optional()])

class PasswordResetForm(Form):
    email = TextField('Email', validators = [Email()])

class ReleaseForm(Form):
    release_version = TextField('Version', validators = [InputRequired()])

class ContactForm(Form):
    message = TextAreaField("Your Message", validators=[InputRequired()])
