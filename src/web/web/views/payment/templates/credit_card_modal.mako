## This file is intended to be included by other files.

<%def name="html()">
    <form method="post" id="credit-card-form">
        <fieldset>
            <div id="credit-card-modal" class="modal" tabindex="-1" role="dialog">
                <div class="modal-dialog">
                    <div class="modal-content">
                        <div class="modal-header">
                            <button type="button" class="close" data-dismiss="modal">×</button>
                            <h4>${caller.title()}</h4>
                        </div>
                        <div class="modal-body">
                            ${caller.description()}

                            ## The same style as the form in manage_subscription.mako
                            <div style="margin-left: 80px; margin-top: 16.5pt;">
                                ${_input_fields()}
                            </div>
                        </div>
                        <div class="modal-footer">
                            <a href="#" class="btn btn-default" data-dismiss="modal">Cancel</a>
                            <button class="btn btn-primary" id="credit-card-submit" type="submit">
                                ${caller.okay_button_text()}
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </fieldset>
    </form>
</%def>

<%def name="default_title()">
    Please upgrade your AeroFS plan
</%def>

<%def name="default_description()">
    <p>
        Please enter your payment information below. We will adjust your
        subscription automatically as you add or remove users, so you
        never have to worry!
    </p>
</%def>

<%def name="default_okay_button_text()">
    Continue
</%def>

<%def name="_input_fields()">
    <%namespace name="csrf" file="csrf.mako"/>
    ${csrf.token_input()}

    <div class="row">
        <div class="col-sm-12">
            <label for="card-number">Card number:</label>
            <input id="card-number" autocomplete="off" class="form-control" type="text"
                   placeholder="•••• •••• •••• ••••">
        </div>
    </div>

    <div class="row">
        <div class="col-sm-6">
            <label for="card-expiry-month">Expires:</label>
            <input id="card-expiry-month" autocomplete="off" class="form-control" type="text"
                   placeholder="MM"> /
            <input id="card-expiry-year" autocomplete="off" class="form-control" type="text"
                   placeholder="YY">
        </div>
        <div class="col-sm-6">
            <label for="card-cvc">Card code:</label>
            <input id="card-cvc" autocomplete="off" class="form-control" type="text"
                   placeholder="CVC">
        </div>
    </div>
</%def>

<%def name="javascript()">
    <script type="text/javascript" src="${request.static_path('web:static/js/stripe.v1.js')}"></script>
    <script type="text/javascript">
        $(document).ready(function() {
            Stripe.setPublishableKey('${stripe_publishable_key}');

            $("#credit-card-modal").on("shown.bs.tab", function() {
                $("#card-number").focus();
            });

            $("#credit-card-form").submit(function() {
                disableSubmission();
                Stripe.createToken({
                    number: $('#card-number').val(),
                    cvc: $('#card-cvc').val(),
                    exp_month: $('#card-expiry-month').val(),
                    exp_year: $('#card-expiry-year').val()
                }, stripeResponseHandler);
                return false;
            });

            function stripeResponseHandler(status, response) {
                if (response.error) {
                    ## show the errors on the form
                    showErrorMessage(response.error.message);
                    enableSubmission();
                } else {
                    ## response['id'] is the Stripe customer ID
                    onStripeCardTokenCreated(response['id'], disposeModal,
                            enableSubmission);
                }
            }

            function disposeModal() {
                $('#credit-card-modal').modal('hide');
            }
        });

        function enableSubmission() {
            $('#credit-card-submit').prop('disabled', false);
        }

        function disableSubmission() {
            $('#credit-card-submit').prop('disabled', true);
        }

        function clearFields() {
            $('#card-number').val("");
            $('#card-cvc').val("");
            $('#card-expiry-month').val("");
            $('#card-expiry-year').val("");
        }

        ## See the comment above inputCreditCardInfo() for the contract of the
        ## callback function.
        var onStripeCardTokenCreated;

        ## Prompt the user to input card info, create a Stripe token for the
        ## card, and pass it to the callback function on successful execution.
        ## On failures, error messages are printed using showErrorMessage().
        ##
        ## The callback accepts the following function signature:
        ##      onStripeCardTokenCreated(token, done, fail);
        ##  token: the credit card token
        ##  done, always: functions the callback should call on success and
        ##              after execution. It's the callback' responsibility to
        ##              display error messages on failures.
        function inputCreditCardInfo(callback) {
            onStripeCardTokenCreated = callback;
            enableSubmission();
            clearFields();

            $('#credit-card-modal').modal('show');
        }

        ## Prompt the user to input card info, create a Stripe token for the
        ## card, create a Stripe customer using the token, and call the callback
        ## on successful execution. On failures, error messages are displayed
        ## using showErrorMessage().
        ##
        ## The callback accepts the following function signature:
        ##      onCustomerCreated(done, fail);
        ##  done, always: functions the callback should call on success and
        ##              after execution. It's the callback' responsibility to
        ##              display error messages on failures.
        ## The callback can be None
        function inputCreditCardInfoAndCreateStripeCustomer(callback) {
            inputCreditCardInfo(function(token, done, always) {
                $.post("${request.route_path('json.create_stripe_customer')}", {
                    "${url_param_stripe_card_token}": token
                }).done(function() {
                    ## retry inviting after the Stripe customer ID is set
                    if (callback) callback(done, always);
                    else done();
                }).fail(function(xhr) {
                    showErrorMessageFromResponse(xhr);
                }).always(function() {
                    if (!callback) always();
                });
            });
        }

        function getCreditCardModal() {
            return $('#credit-card-modal');
        }
    </script>
</%def>
