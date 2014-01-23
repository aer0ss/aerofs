import datetime

from flask.ext.wtf import Form
from wtforms import TextField, PasswordField, HiddenField, BooleanField, IntegerField, DateField
from wtforms.validators import ValidationError, InputRequired, Email, Length, Optional, EqualTo

class LoginForm(Form):
    email = TextField('Email', validators=[Email()])
    password = PasswordField('Password', validators=[InputRequired()])

class SignupForm(Form):
    def validate_accept_eula(form, field):
        if True != field.data:
            raise ValidationError("You must accept the License Agreement to proceed")
    first_name = TextField('First Name', validators=[InputRequired()])
    last_name = TextField('Last Name', validators=[InputRequired()])
    email = TextField('Email', validators = [Email()])
    company_name = TextField('Organization Name', validators=[InputRequired()])
    phone_number = TextField("Phone Number", validators=[Optional()])
    job_title = TextField("Job Title", validators=[Optional()])
    accept_eula = BooleanField("I accept the License Agreement")

class CompleteSignupForm(Form):
    password = PasswordField('Password', validators=[Length(min=6)])

class AcceptInviteForm(Form):
    def validate_accept_eula(form, field):
        if True != field.data:
            raise ValidationError("You must accept the License Agreement to proceed")
    first_name = TextField('First Name', validators=[InputRequired()])
    last_name = TextField('Last Name', validators=[InputRequired()])
    password = PasswordField('Password', validators=[Length(min=6)])
    phone_number = TextField("Phone Number", validators=[Optional()])
    job_title = TextField("Job Title", validators=[Optional()])
    accept_eula = BooleanField("I accept the License Agreement")

class InviteForm(Form):
    email = TextField('Email', validators = [Email()])

class PreferencesForm(Form):
    first_name = TextField('First Name', validators=[InputRequired()])
    last_name = TextField('Last Name', validators=[InputRequired()])
    password = PasswordField("Password", validators=[
        Optional(),
        Length(min=6),
        EqualTo('password_confirmation', message='Passwords must match')
        ])
    password_confirmation = PasswordField("Password confirmation")
    security_emails = BooleanField("Receive security notifications")
    release_emails = BooleanField("Receive release notifications")
    maintenance_emails = BooleanField("Receive maintenance notifications")

def IsFutureDate(message=None):
    def _IsFutureDate(form, field):
        if field.data < datetime.datetime.today().date():
            raise ValidationError(u"Date must be in the future")
    return _IsFutureDate

class InternalLicenseRequestForm(Form):
    # BIG TODO: make this not suck
    # TODO: make this a dropdown or something?  infer from URL?  something?
    org_id = IntegerField("Org ID", validators=[InputRequired()])
    seats = IntegerField("Seats", validators=[InputRequired()])
    expiry_date = DateField("Expiry Date (YYYY-MM-DD)", validators=[InputRequired(), IsFutureDate() ])
    is_trial = BooleanField("Trial?")
    allow_audit = BooleanField("Allow Audit?")
