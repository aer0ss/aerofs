import flask_login as login
from flask import flash, redirect, url_for, render_template
from lizard import models, db, forms


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


def _get_promo_biz30(code):
    return render_template('promo_biz30.html', form=forms.PromoForm(code=get_code_for(code)))


def _get_promo_biz30_spiceworks():
    return _get_promo_biz30('biz30_spiceworks')


def _get_promo_biz30_adwords():
    return _get_promo_biz30('biz30_adwords')


def _get_promo_biz90():
    return render_template('promo_biz90.html', form=forms.PromoForm(code=get_code_for('biz90')))


def _create_new_biz_license(days):
    """
    Issue a Business license with all features for the given number of days.
    """
    user = login.current_user
    l = models.License()
    l.customer = user.customer
    l.state = models.License.LicenseState.PENDING
    l.seats = 30
    l.set_days_until_expiry(days)
    l.is_trial = True
    l.allow_audit = True
    l.allow_identity = True
    l.allow_mdm = True
    l.allow_device_restriction = True
    db.session.add(l)
    db.session.commit()


def _post_promo_biz30():
    _create_new_biz_license(30)
    flash(u"Your 30-day trial has started", "success")
    return redirect(url_for(".dashboard"))


def _post_promo_biz90():
    _create_new_biz_license(90)
    flash(u"Your 90-day trial has started", "success")
    return redirect(url_for(".dashboard"))


# The only reason to map uuid to promotion is to obscure the code and to discourage
# guessing/probing the backend for promotions.
_promotions = [
    # policy, code, get_handler, post_handler
    # The quick and dirty way to expire a policy is to comment out the policy here.

    # Spiceworks.
    (
        'biz30_spiceworks',
        '6BAF7D67-E219-46F5-B722-FE4508682F5B',
        _get_promo_biz30_spiceworks,
        _post_promo_biz30),
    # AdWords.
    (
        'biz30_adwords',
        '299F7A32-92EE-4E49-86EF-F96CB79924F9',
        _get_promo_biz30_adwords,
        _post_promo_biz30),
    # Email attachment manager campaign.
    (
        'biz90',
        'B10E4FF3-7036-426C-8E23-106A81E8745E',
        _get_promo_biz90,
        _post_promo_biz90),
]
