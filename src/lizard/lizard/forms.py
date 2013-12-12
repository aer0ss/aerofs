from flask.ext.wtf import Form
from wtforms import TextField, PasswordField, HiddenField, BooleanField
from wtforms.validators import InputRequired, Email, Length, Optional, EqualTo

class LoginForm(Form):
    email = TextField('Email', validators=[Email()])
    password = PasswordField('Password', validators=[InputRequired()])

class SignupForm(Form):
    company_name = TextField('Organization Name', validators=[InputRequired()])
    name = TextField('Your Full Name', validators=[InputRequired()])
    password = PasswordField('Password', validators=[Length(min=6)])

class AcceptForm(Form):
    name = TextField('Your Full Name', validators=[InputRequired()])
    password = PasswordField('Password', validators=[Length(min=6)])

class InviteForm(Form):
    email = TextField('Email', validators = [Email()])

class PreferencesForm(Form):
    name = TextField("Name", validators=[InputRequired()])
    password = PasswordField("Password", validators=[
        Optional(),
        Length(min=6),
        EqualTo('password_confirmation', message='Passwords must match')
        ])
    password_confirmation = PasswordField("Password confirmation")
    security_emails = BooleanField("Receive security notifications")
    release_emails = BooleanField("Receive release notifications")
    maintenance_emails = BooleanField("Receive maintenance notifications")
