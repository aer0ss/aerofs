## This file is intended to be included by other files.

<%def name="html()">

    <form method="post" id="credit-card-form">
        <fieldset>
            <div id="credit-card-modal" class="modal hide" tabindex="-1" role="dialog">
                <div class="modal-header">
                    <button type="button" class="close" data-dismiss="modal">×</button>
                    <h4>${caller.title()}</h4>
                </div>
                <div class="modal-body">
                    ${caller.description()}

                    <div style="margin-left: 80px; margin-top: 16.5pt;">
                        ${_input_fields()}
                    </div>
                </div>
                <div class="modal-footer">
                    <a href="#" class="btn" data-dismiss="modal">Cancel</a>
                    <button class="btn btn-primary" id="credit-card-submit" type="submit">
                        ${caller.okay_button_text()}
                    </button>
                </div>
            </div>
        </fieldset>
    </form>
</%def>

<%def name="_input_fields()">
    <%namespace name="csrf" file="csrf.mako"/>
    ${csrf.token_input()}

    <div class="row">
        <div class="span4">
            <label for="card-number">Card number:</label>
            <input id="card-number" autocomplete="off" class="span4" type="text"
                   placeholder="•••• •••• •••• ••••">
        </div>
    </div>

    <div class="row">
        <div class="span2">
            <label for="card-expiry-month">Expires:</label>
            <input id="card-expiry-month" autocomplete="off" class="span1" type="text"
                   placeholder="MM"> /
            <input id="card-expiry-year" autocomplete="off" class="span1" type="text"
                   placeholder="YY">
        </div>
        <div class="span2">
            <label for="card-cvc">Card code:</label>
            <input id="card-cvc" autocomplete="off" class="span2" type="text"
                   placeholder="CVC">
        </div>
    </div>
</%def>

<%def name="javascript()">
    <script type="text/javascript" src="https://js.stripe.com/v1/"></script>
    <script type="text/javascript">
        $(document).ready(function() {
            Stripe.setPublishableKey('${stripe_publishable_key}');

            $("#credit-card-modal").on("shown", function() {
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

        ## See the comment above inputCreditCardInfo() for the contract of the
        ## callback function.
        var onStripeCardTokenCreated;

        ## The callback accepts the following function signature:
        ##      onStripeCardTokenCreated(token, done, fail);
        ##  token: the credit card token
        ##  done, fail: functions the callback should call on success and
        ##              failure. It's the callback' responsibility to display
        ##              error messages on failures.
        function inputCreditCardInfo(callback) {
            onStripeCardTokenCreated = callback;
            enableSubmission();
            clearFields();

            $('#credit-card-modal').modal('show');
        }

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

    </script>
</%def>