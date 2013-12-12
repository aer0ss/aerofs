from flask.ext.wtf import Form
from wtforms import TextField, BooleanField, PasswordField, HiddenField
from wtforms.validators import InputRequired, Email, Length, EqualTo, Optional

class LoginForm(Form):
    email = TextField('Email', validators=[Email()])
    password = PasswordField('Password', validators=[InputRequired()])

class SignupForm(Form):
    # Form for a new user signing up her own company.
    # We use the signup code rather than the email because:
    # 1) the email is implied by the signup code
    # 2) using just the email would let users create accounts for addresses they don't own
    signup_code = HiddenField('signup_code', validators=[InputRequired()])
    company_name = TextField('Organization Name', validators=[InputRequired()])
    name = TextField('Your Full Name', validators=[InputRequired()])
    password = PasswordField('Password', validators=[Length(min=6)])

class InviteForm(Form):
    email = TextField('Email', validators = [Email()])
