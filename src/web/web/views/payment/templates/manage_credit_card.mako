<%inherit file="layout.mako"/>
<%! navigation_bars = True; %>

## Without the page_block div, the heading and the form has less spacing than
## other pages. I didn't have time to investigate why.
<div class="page_block">
    <h2>Manage Credit Card</h2>
</div>

<p>
    %if card:
        Card Type: ${card['type']}<br>
        Card Number: ************${card['last_four_digits']}<br>
        Exp. Date: ${card['expires_month']}/${card['expires_year']}<br>
    %else:
        ## TODO (WW) can 'card' be None at all?
        No card on record.
    %endif
</p>

<p>
    Please enter your new card information:
</p>

<div class="row">
    <div class="span4">
        <form method="post" id="payment-form">
            <fieldset>
                ${self.csrf_token_input()}

                <%include file="credit_card_inputs.mako" />

                <div class="controls">
                    <input type="submit" class="btn" id="submit-button" value="Update">
                </div>
            </fieldset>
        </form>
    </div>
</div>

<%block name="scripts">
    <script type="text/javascript" src="https://js.stripe.com/v1/"></script>
    <script type="text/javascript">
        $(document).ready(function() {
            Stripe.setPublishableKey('${stripe_publishable_key}');

            var stripeTokenId = "stripeToken";
            var $form = $("#payment-form");

            function stripeResponseHandler(status, response) {
                if (response.error) {
                    ## show the errors on the form
                    showErrorMessage(response.error.message);
                    formEnabled(true);

                } else {
                    ## response contains id, last4, and card type
                    var token = response['id'];

                    ## remove the stripe token if one already exists, this may happen
                    ## because we are using AJAX and the Stripe API call can succeed
                    ## resulting in the hidden form field and then our AJAX call to
                    ## SP via web may fail, we want the user to be able to retry
                    $form.find('input[name="' + stripeTokenId + '"]').remove();

                    ## insert the token into the form so it gets submitted to the
                    ## server
                    $form.append("<input type='hidden' name='" + stripeTokenId +
                            "' value='" + token + "'/>");

                    submitPaymentForm();
                }
            }

            function formEnabled(enabled) {
                $form.find('#submit-button').prop('disabled', !enabled);
            }

            function submitPaymentForm() {
                var serializedData = $form.serialize();

                $.ajax({
                    url: $form.attr('action'),
                    type: $form.attr('method'),
                    data: serializedData
                })
                .done(function(response) {
                    ## reload the page to refresh the current credit card number.
                    ## TODO (WW) also show a successful message
                    window.location.href = '${request.url}';
                })
                .fail(function(jqXHR, textStatus, errorThrown) {
                    showErrorMessage("An error occurred processing your request. " +
                                     "Please try again. (" + errorThrown + ")");
                })
                .always(function() {
                    formEnabled(true);
                });
            }

            $('#card-number').focus();

            $form.submit(function(event) {
                try {
                    ## disable the submit button to prevent repeated clicks
                    formEnabled(false);

                    Stripe.createToken({
                        number: $('#card-number').val(),
                        cvc: $('#card-cvc').val(),
                        exp_month: $('#card-expiry-month').val(),
                        exp_year: $('#card-expiry-year').val()
                    }, stripeResponseHandler);

                } catch (e) {
                    console.error(e.message, e);
                } finally {
                    ## prevent the form from submitting with the default action
                    return false;
                }
            });
        });
    </script>
</%block>
