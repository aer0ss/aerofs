<%inherit file="layout.mako"/>

<style type="text/css">
    .control-group {
        ## We have to use control-group to implement validation state, but
        ## we don't want control-group's large margin-bottom.
        margin-bottom: 0;
    }
</style>

<div class="offset4 span4">
    <form method="post" id="payment-form">
        <fieldset>
            ${self.csrf_token_input()}
            <h2>Complete Your Free Trial</h2>
            <div id="organization-name" class="control-group">
                <div class="controls controls-row">
                    ## NB. Don't use input-block-level, as it would make the
                    ## field's height smaller than the ones that use span*.
                    ## Maybe upgrading Stripe would solve the problem?
                    <label for="org_name">Team name:</label>
                    <input name="${ORG_NAME_FIELD_NAME}" class="span4"
                            id="org_name" type="text" placeholder="e.g. ACME Inc">
                </div>
            </div>
            <div id="organization-phone" class="control-group">
                <div class="controls controls-row">
                    <label for="org_phone">Contact phone number:</label>
                    <input name="${ORG_CONTACT_PHONE_FIELD_NAME}" class="span4"
                            id="org_phone" type="text" placeholder="e.g. 111-222-3333">
                </div>
            </div>

            <br>

            <div class="well well-small">Your credit card will not be charged
            during your 14-day trial. Cancel at any time.</div>

            <%include file="credit_card_inputs.mako" />

            <br>

            <div class="control-group">
                <div class="controls">
                    <input type="submit" class="btn" id="submit-button" value="Start Free Trial">
                </div>
            </div>

        </fieldset>
    </form>

    <p>
        Got questions? Email <a href="mailto:business@aerofs.com" target="_blank">business@aerofs.com</a><br>
    </p>
</div>

<%block name="scripts">
    <script type="text/javascript" src="https://js.stripe.com/v1/"></script>
    <script type="text/javascript">
        $(document).ready(function() {
            Stripe.setPublishableKey('${STRIPE_PUBLISHABLE_KEY}');

            var stripeTokenId = "stripeToken";
            var $form = $("#payment-form");

            function stripeResponseHandler(status, response) {
                if (response.error) {
                    // show the errors on the form
                    showErrorMessage(response.error.message);
                    formEnabled(true);
                } else {
                    // response contains id, last4, and card type
                    var token = response['id'];

                    // remove the stripe token if one already exists, this may happen
                    // because we are using AJAX and the Stripe API call can succeed
                    // resulting in the hidden form field and then our AJAX call to
                    // SP via web may fail, we want the user to be able to retry
                    $form.find('input[name="' + stripeTokenId + '"]').remove();

                    // insert the token into the form so it gets submitted to the
                    // server
                    $form.append("<input type='hidden' name='" + stripeTokenId + "' value='" + token + "'/>");

                    submitPaymentForm();
                }
            }

            function formEnabled(enabled) {
                $form.find('#submit-button')
                     .prop('disabled', !enabled);
            }

            function submitPaymentForm() {
                var serializedData = $form.serialize();

                $.ajax({
                    url: $form.attr('action'),
                    type: $form.attr('method'),
                    data: serializedData
                })
                .done(function(response) {
                    ## TODO (WW) we will be stuck if track() fails. But I don't have a good solution
                    mixpanel.track("Activated Business Plan", {}, function() {
                        window.location.href =
                                '${request.route_url('business_activate_done')}';
                    });
                })
                .fail(function(jqXHR, textStatus, errorThrown) {
                    showErrorMessage("An error occurred processing your request. " +
                                     "Please try again. (" + errorThrown + ")");
                })
                .always(function() {
                    formEnabled(true);
                });
            }

            function validateOrgInputs() {
                var validOrgName = validateOrgName($("#organization-name"));
                if (!validOrgName) return false;

                var validOrgPhone = validateOrgPhone($("#organization-phone"));
                if (!validOrgPhone) return false;

                return true;
            }

            function validateOrgName($orgNameGroup) {
                $orgNameGroup.attr("class", "control-group");

                var orgName = $orgNameGroup.find("input").val();
                if ($.trim(orgName) === "") {
                    $orgNameGroup.addClass("error");
                    showErrorMessage("Please enter a team name.");
                    return false;
                }

                return true;
            }

            function validateOrgPhone($orgPhoneGroup) {
                $orgPhoneGroup.attr("class", "control-group");

                var orgPhone = $orgPhoneGroup.find("input").val();
                if ($.trim(orgPhone) === "") {
                    $orgPhoneGroup.addClass("error");
                    showErrorMessage("Please enter a contact phone number.");
                    return false;
                }

                return true;
            }

            $('#org_name').focus();

            $form.submit(function(event) {
                try {
                    // disable the submit button to prevent repeated clicks
                    formEnabled(false);

                    var validInputs = validateOrgInputs();

                    if (!validInputs) {
                        formEnabled(true);
                    } else {
                        Stripe.createToken({
                            number: $('#card-number').val(),
                            cvc: $('#card-cvc').val(),
                            exp_month: $('#card-expiry-month').val(),
                            exp_year: $('#card-expiry-year').val()
                        }, stripeResponseHandler);
                    }
                } catch (e) {
                    console.error(e.message, e);
                } finally {
                    // prevent the form from submitting with the default action
                    return false;
                }
            });
        });
    </script>
</%block>
