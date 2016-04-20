import datetime

from flask_wtf import Form
from flask_wtf.file import FileField, FileRequired
from wtforms import StringField, PasswordField, BooleanField, IntegerField, DateField, SelectField, TextAreaField, HiddenField
from wtforms.validators import ValidationError, InputRequired, Email, Length, Optional, NumberRange, Regexp, IPAddress

class LoginForm(Form):
    email = StringField('Email', validators=[Email()])
    password = PasswordField('Password')

class SignupForm(Form):
    first_name = StringField('First Name', validators=[InputRequired()])
    last_name = StringField('Last Name', validators=[InputRequired()])
    email = StringField('Email', validators = [InputRequired(), Email()])
    company_name = StringField('Company', validators=[InputRequired()])
    phone_number = StringField("Phone", validators=[InputRequired()])
    job_title = StringField("Job Title", validators=[InputRequired()])
    company_size = StringField("Company Size", validators=[Optional()])
    current_fss = StringField("Current file sharing solution", validators=[Optional()])
    country = StringField("Country", validators=[Optional()])
    price_plan = HiddenField("price_plan", validators=[Optional()])
    promo_code = HiddenField("promo_code", validators=[Optional()])
    demandramp_rm__utm_medium__c = HiddenField("demandramp_rm__utm_medium__c", validators=[Optional()])
    demandramp_rm__utm_source__c = HiddenField("demandramp_rm__utm_source__c", validators=[Optional()])
    demandramp_rm__utm_campaign__c = HiddenField("demandramp_rm__utm_campaign__c", validators=[Optional()])
    demandramp_rm__utm_content__c = HiddenField("demandramp_rm__utm_content__c", validators=[Optional()])
    demandramp_rm__utm_term__c = HiddenField("demandramp_rm__utm_term__c", validators=[Optional()])
    demandramp_rm__referring_url__c = HiddenField("demandramp_rm__referring_url__c", validators=[Optional()])
    demandramp_rm__destination_url__c = HiddenField("demandramp_rm__destination_url__c", validators=[Optional()])
    demandramp_rm__form_fill_out_url__c = HiddenField("demandramp_rm__form_fill_out_url__c", validators=[Optional()])
    demandramp_rm__landing_page_url__c = HiddenField("demandramp_rm__landing_page_url__c", validators=[Optional()])
    demandramp_rm__person_id__c = HiddenField("demandramp_rm__person_id__c", validators=[Optional()])
    demandramp_rm__session_id__c = HiddenField("demandramp_rm__session_id__c", validators=[Optional()])

class NewAccountCreationForm(Form):
    first_name = StringField('First Name', validators=[InputRequired()])
    last_name = StringField('Last Name', validators=[InputRequired()])
    email = StringField('Email', validators = [InputRequired(), Email()])
    company_name = StringField('Company', validators=[InputRequired()])
    phone_number = StringField("Phone", validators=[InputRequired()])
    job_title = StringField("Job Title", validators=[InputRequired()])
    company_size = SelectField("Company Size", choices=[
            ('0-74', '0-74'),
            ('75-149', '75-149'),
            ('150-499', '150-499'),
            ('500-999', '500-999'),
            ('1000- 2499', '1000-2499'), # the extra space matches the marketing form
            ('2500-4999', '2500-4999'),
            ('5000+', '5000+')
        ], validators=[InputRequired()], default="0-74")
    current_fss = SelectField("Current file sharing solution", choices=[
            ('SharePoint', 'SharePoint'),
            ('Box', 'Box'),
            ('Dropbox', 'Dropbox'),
            ('File Server', 'File Server'),
            ('Other', 'Other'),
            ('None', 'None'),
            ("Don't know", "Don't know")
        ], validators=[InputRequired()], default="Other")
    country = StringField("Country", validators=[InputRequired()], default="United States")

class CompleteSignupForm(Form):
    password = PasswordField('Password', validators=[InputRequired(), Length(min=6)])

class AcceptInviteForm(Form):
    first_name = StringField('First Name', validators=[InputRequired()])
    last_name = StringField('Last Name', validators=[InputRequired()])
    password = PasswordField('Password', validators=[InputRequired(), Length(min=6)])
    phone_number = StringField("Phone Number", validators=[Optional()])
    job_title = StringField("Job Title", validators=[Optional()])

class InviteForm(Form):
    email = StringField('Email address', validators = [Email()])

class PreferencesForm(Form):
    first_name = StringField('First Name', validators=[InputRequired()])
    last_name = StringField('Last Name', validators=[InputRequired()])
    customer_name = StringField('Organization', validators=[InputRequired()])
    # DF: these fields disabled until we figure out our story with email notifications
    #security_emails = BooleanField("Receive security notifications")
    #release_emails = BooleanField("Receive release notifications")
    #maintenance_emails = BooleanField("Receive maintenance notifications")

class LicenseCountForm(Form):
    count = IntegerField('New License Count', validators=[NumberRange(min=1,max=1000,message="Purchase between 1 and 1,000 seats")])

class PromoForm(Form):
    # this field optional because it is hidden and we handle the error ourselves
    code = HiddenField('promo_code', validators=[Optional()])

def IsFutureDate(message=None):
    def _IsFutureDate(form, field):
        if field.data < datetime.datetime.today().date():
            raise ValidationError(u"Date must be in the future")
    return _IsFutureDate

class InternalLicenseRequestForm(Form):
    seats = IntegerField("Seats", validators=[InputRequired()])
    expiry_date = DateField("Expiry Date (YYYY-MM-DD)", validators=[InputRequired(), IsFutureDate() ])
    is_trial = BooleanField("Free?")
    allow_audit = BooleanField("Allow Audit?")
    allow_identity = BooleanField("Allow LDAP/AD/OpenID?")
    allow_mdm = BooleanField("Allow MDM?")
    allow_device_restriction = BooleanField("Allow Device Restriction?")
    manual_invoice = StringField("Manual Invoice ID?(Required for manual license requests)", validators=[Optional()])
    stripe_subscription_id = StringField("Stripe Subscription ID?", validators=[Optional()])

class InternalLicenseBundleUploadForm(Form):
    license_bundle = FileField("License bundle:", validators=[FileRequired()])

class InternalLicenseStateForm(Form):
    # Note: _licensehelper.html has some macros that depend on the name of this field
    state = SelectField(u'Queue', choices=[
        ('PENDING', 'PENDING'),
        ('ON_HOLD', 'ON HOLD'),
        ('IGNORED', 'IGNORED')
    ], validators=[Optional()])
    invoice_id = StringField("Manual Invoice ID", validators=[Optional()])
    stripe_subscription_id = StringField("Stripe Subscription ID", validators=[Optional()])

class PasswordResetForm(Form):
    email = StringField('Email', validators = [Email()])

class ReleaseForm(Form):
    release_version = StringField('Version', validators = [InputRequired()])

class ContactForm(Form):
    contact = SelectField(u'Department', choices=[
        ('SALES', 'Sales'),
        ('SUPPORT', 'Support')
    ], validators=[InputRequired()])
    subject = TextAreaField("Subject", validators=[InputRequired()])
    message = TextAreaField("Your Message", validators=[InputRequired()])

class AllAccountsSearchForm(Form):
    search_terms = StringField('Search Terms')

# This form is used to create a new Hosted Private Cloud Deployment
class CreateHostedDeployment(Form):
    customer_id = StringField("Customer ID", validators=[InputRequired()])
    subdomain = StringField("Subdomain", validators=[
        InputRequired(),
        Regexp('^[a-z0-9][a-z0-9-]*[a-z0-9]$',
               message="Only letters, numbers and dashes are allowed for the subdomain, and it can't start or end with a dash.")])
    server_id =IntegerField("Server ID (Optional, if not filled in we pickup the most relevant server)",
                            validators=[Optional()])

# This form is used to create a new Hosted Private Cloud Server
class AddHPCServer(Form):
    server_name = StringField("New server name", validators=[InputRequired()])
