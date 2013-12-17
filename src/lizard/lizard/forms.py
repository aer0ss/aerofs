from flask.ext.wtf import Form
from wtforms import TextField, PasswordField, HiddenField, BooleanField
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
