#!env/bin/python
import argparse
import datetime
import sys

from lizard import create_app, db, models

app = create_app()

def make_new_org(org_name):
    o = models.Customer()
    o.name = org_name
    o.active = True
    o.renewal_seats = 25 # default
    db.session.add(o)
    return o

def make_new_user(email, password, first_name, last_name, customer):
    u = models.Admin()
    u.email = email
    u.customer = org
    u.first_name = first_name
    u.last_name = last_name
    u.set_password(password)
    db.session.add(u)
    return u

def make_new_license(org, days, seats, full, audit, identity, mdm, device_restriction):
    l = models.License()
    l.customer = org
    l.state = models.License.LicenseState.FILLED
    l.seats = seats
    today = datetime.datetime.today().date()
    expiry_date = datetime.datetime.combine(today + datetime.timedelta(days=days), datetime.datetime.min.time())
    l.expiry_date = expiry_date
    l.is_trial = not full
    l.allow_audit = audit
    l.allow_identity = identity
    l.allow_mdm = mdm
    l.allow_device_restriction = device_restriction
    l.blob = u"This is a fake license file."
    db.session.add(l)
    return l

def user_exists(email):
    return len(models.Admin.query.filter_by(email=email).all()) != 0

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--org-name", help="set an organization name", default=u"Test Company")
    parser.add_argument("--org-id", type=int, help="don't create an org, but use the existing one with the specified ID")
    parser.add_argument("--password", help="set a specific password", default=u"temp123")
    parser.add_argument("--first-name", help="create user with a particular first name", default=u"Firsty")
    parser.add_argument("--last-name", help="create user with a particular last name", default=u"Lasto")
    parser.add_argument("--license-days", type=int, help="create license valid until a particular number of days from now", default=30)
    parser.add_argument("--license-seats", type=int, help="create license with a particular number of seats", default=30)
    parser.add_argument("--license-full", help="create non-trial license", action="store_false")
    parser.add_argument("--license-audit", help="allow auditing", action="store_true")
    parser.add_argument("--license-identity", help="allow identity", action="store_true")
    parser.add_argument("--license-mdm", help="allow MDM", action="store_true")
    parser.add_argument("--license-device-restriction", help="allow device restriction", action="store_true")

    parser.add_argument("email", nargs="+", help="email addresses to create accounts for")
    args = parser.parse_args(sys.argv[1:])
    # We need a request context to have the flask request globals available (db connection, for one)
    ctx = app.test_request_context('/?next=http://example.com')
    with ctx:
        org = None
        if args.org_id != None:
            org = models.Customer.query.get(args.org_id)
            if not org:
                raise ValueError("No org with specified id: {}".format(args.org_id))
        else:
            print "Adding org '{}'".format(args.org_name)
            org = make_new_org(args.org_name)

        for mail in args.email:
            email = mail.decode('utf-8')
            if not user_exists(email):
                print "creating Admin {} ({} {})".format(email, args.first_name, args.last_name)
                make_new_user(email, args.password, args.first_name, args.last_name, org)
            else:
                print "{} already exists, moving on".format(email)
        print "Creating fake license ({} days, {} seats, {}, {}, {}, {}, {})".format(
                args.license_days, args.license_seats, "non-trial" if args.license_full else "trial",
                "auditing allowed" if args.license_audit else "no audit",
                "identity allowed" if args.license_identity else "no identity",
                "mdm allowed" if args.license_mdm else "no mdm",
                "device restriction allowed" if args.license_device_restriction else "no device restriction")
        l = make_new_license(org, args.license_days, args.license_seats, args.license_full, args.license_audit, args.license_identity, args.license_mdm, args.license_device_restriction)
        db.session.commit()
