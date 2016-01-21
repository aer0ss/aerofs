import flask_login as login
import markupsafe
from flask import flash, redirect, url_for, render_template
from lizard import models, db, forms, analytics_client

E_INVALID_CODE = "The promotion code you provided is not valid or has expired. " \
                 "Please contact AeroFS Support for assistance."


def get_promo(code):
    get = [f for (_, c, f, _) in _promotions if c == code]

    if get:
        return get[0]()
    else:
        return render_template('promo_invalid_code.html')


def post_promo(code):
    post = [f for (_, c, _, f) in _promotions if c == code]

    if post:
        return post[0]()
    else:
        return render_template('promo_invalid_code.html')


def get_code_for(policy):
    return [c for (p, c, _, _) in _promotions if p == policy][0]


def _get_promo_biz90():
    # note that we can't distinguish between users refreshing the page
    # vs. users seeing the promotion but choosing not to activate it.
    #
    # TODO: verify the following commented out code works and uncomment them
    # user = login.current_user
    #
    # analytics_client.track(user.customer_id, "Viewed Biz90", {
    #     'email': markupsafe.escape(user.email)
    # })

    return render_template('promo_biz90.html', form=forms.PromoForm(code=get_code_for('biz90')))


def _post_promo_biz90():
    """
    Issue a Business license with all features for 90 days.
    """
    user = login.current_user

    l = models.License()
    l.customer = user.customer
    l.state = models.License.LicenseState.PENDING
    l.seats = 30
    l.set_days_until_expiry(90)
    l.is_trial = True
    l.allow_audit = True
    l.allow_identity = True
    l.allow_mdm = True
    l.allow_device_restriction = True

    db.session.add(l)
    db.session.commit()

    # TODO: verify the following commented out code works and uncomment them
    # analytics_client.track(user.customer_id, "Activated Biz90", {
    #     'email': markupsafe.escape(user.email)
    # })

    flash(u"Your 90-day trial has started", "success")
    return redirect(url_for(".dashboard"))

# the only reason to map uuid to promotion is to obscure the code and to discourage
# guessing/probing the backend for promotions.
_promotions = [
    # policy, code, get_handler, post_handler
    # the quick and dirty way to expire a policy is to comment out the policy here
    ('biz90', 'B10E4FF3-7036-426C-8E23-106A81E8745E', _get_promo_biz90, _post_promo_biz90),
]
