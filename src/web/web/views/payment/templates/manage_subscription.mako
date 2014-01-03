<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Subscription" %>

<%namespace name="credit_card_modal" file="credit_card_modal.mako"/>

<%block name="css">
    <style type="text/css">
        .symbol {
            margin: auto 12px;
        }
    </style>
</%block>

<div class="page-block">
    <h2>Current Subscription</h2>

    <p>You are paying (in US dollars):</p>

    <p>
        <span class="lead"><strong>${quantity}</strong></span>
        %if quantity == 1:
                member
        %else:
                members
        %endif
        <span class="lead symbol">&#x00D7;</span>
        <span class="lead"><strong>$${unit_price_dollars}</strong></span> /month
        <span class="lead symbol">=</span>
        <span class="lead"><strong>$${quantity * unit_price_dollars}</strong></span> /month
    </p>

    <p>
        The system will automatically adjust the subscription when you
        <a href="${request.route_path('org_users')}">add or remove members</a>.
    </p>
</div>

<div class="page-block">
    <h2>Payment Method</h2>
    <div class="row">
        <div class="span3">
            Type: ${card['type']}<br>
            Number: ************${card['last_four_digits']}<br>
            Exp. Date: ${card['expires_month']}/${card['expires_year']}
        </div>
        <div class="span3">
            <a class="btn" href="#" onclick="inputCreditCardInfo(updateCreditCard); return false;">
                Update
            </a>
        </div>
    </div>
</div>

<div class="page-block">
    <h2>Billing History</h2>
    <table class="table">
        <thead>
        <tr>
            <th>Post Date</th>
            <th>Billing Cycle</th>
            <th>Through</th>
            <th>Amount</th>
        </tr>
        </thead>
        <tbody>
                % for invoice in invoices:
                <%
                    ## id = invoice['id']
                    date = invoice['date']
                    period_start = invoice['period_start']
                    period_end = invoice['period_end']
                    total = invoice['total']
                    paid = invoice['paid']

                    ## Format total. TODO (WW) make it a utility function?
                    total = str(total / 100) + '.' + str(format(total % 100)).zfill(2)
                %>
                <tr>
                    <td class="format-time">${date}</td>
                    <td class="format-time">${period_start}</td>
                    <td class="format-time">${period_end}</td>
                    <td class="format-currency">$${total} ${"" if paid else "(Pending)"}</td>
                </tr>
                % endfor
        </tbody>
    </table>
</div>

<div class="page-block">
    <a class="btn" href="#" onclick="cancelSubscription(); return false;">
        Cancel Subscription
    </a>
</div>

<div id="cancel-subscription-modal" class="modal hide" tabindex="-1" role="dialog">
    <div class="modal-header">
        <button type="button" class="close" data-dismiss="modal">×</button>
        <h4>We are sorry to see you go!</h4>
    </div>
    <div class="modal-body">
        <p>
            Are you sure you want to cancel the subscription?
            <strong>All</strong> your users will be removed from your organization.
        </p>

    </div>
    <div class="modal-footer">
        <a href="#" class="btn" data-dismiss="modal">Go Back</a>
        <a href="#" class="btn btn-danger" onclick="cancelSubscriptionFeedback(); return false;">
            Continue</a>
    </div>
</div>

<div id="cancel-subscription-feedback-modal" class="modal hide" tabindex="-1" role="dialog">
    <div class="modal-header">
        <button type="button" class="close" data-dismiss="modal">×</button>
        <h4>We really appreciate your feedback</h4>
    </div>
    <div class="modal-body">
        <p>
            Before you go, we would really like to understand why you chose to
            cancel and how we can improve our product. Please leave your comments below.
        </p>
        ## The same style as the form in credit-card-modal.mako
        <form style="margin-left: 80px; margin-top: 16.5pt;">
            <textarea id="feedback" rows="3" class="span4"></textarea>
        </form>
        <p>Or, Leave your comments and give us a chance. We will see what we can
            do to win back your business, and get back to you within 24 hours.</p>

    </div>
    <div class="modal-footer">
        <a href="#" class="btn" data-dismiss="modal">Go Back</a>
        <a href="#" id="cancel-button" class="btn btn-danger" onclick="executeCancellation(); return false;">
            Cancel Now</a>
        <a href="#" id="chance-button" class="btn btn-primary" onclick="giveChance(); return false;">Give 'em a Chance</a>
    </div>
</div>

<div id="chance-given-modal" class="modal hide" tabindex="-1" role="dialog">
    <div class="modal-header">
        <button type="button" class="close" data-dismiss="modal">×</button>
        <h4>Thank you for giving us an opportunity</h4>
    </div>
    <div class="modal-body">
        <p>We will try best to see what we can do to help.</p>
    </div>
    <div class="modal-footer">
        <a href="#" class="btn" data-dismiss="modal">Close</a>
    </div>
</div>

<div id="subscription-cancelled-modal" class="modal hide" tabindex="-1" role="dialog">
    <div class="modal-header">
        <button type="button" class="close" data-dismiss="modal">×</button>
        <h4>Your subscription has been cancelled</h4>
    </div>
    <div class="modal-body">
        <p>Your account has been downgraded to the free plan.
            Whichever plan you are on, the AeroFS team loves you.</p>
    </div>
    <div class="modal-footer">
        <a href="${request.route_path('dashboard_home')}" class="btn btn-primary">
            Go to Home Page</a>
    </div>
</div>

<%credit_card_modal:html>
    <%def name="title()">
        Edit Payment Method
    </%def>
    <%def name="description()">
        <p>
            Please enter the new payment method:
        </p>
    </%def>
    <%def name="okay_button_text()">
        Update
    </%def>
</%credit_card_modal:html>

<%block name="scripts">
    <%credit_card_modal:javascript/>

    <script src="//ajax.googleapis.com/ajax/libs/jqueryui/1.10.0/jquery-ui.min.js"></script>
    <script type="text/javascript">
        $(document).ready(function() {
            $('.format-time').text(function(i, v) {
                return $.datepicker.formatDate('D, d M yy', new Date(v * 1000));
            });
        });

        ## This method follows the contract defined by inputCreditCardInfo()
        function updateCreditCard(token, done, always) {
            $.post("${request.route_path('json.update_credit_card')}", {
                "${url_param_stripe_card_token}": token
            }).done(function() {
                ## Reload the page to refresh the current payment method.
                ## Do not call done() so the payment dialog will stay until
                ## the page is fully reloaded.
                ##
                ## TODO (WW) also show a successful message?
                ## Don't use "location.href =". It's not supported by old Firefox.
                window.location.assign('${request.url}');
            }).fail(function(xhr) {
                showErrorMessageFromResponse(xhr);
            }).always(always);
        }

        function cancelSubscription() {
            $("#cancel-subscription-modal").modal("show");
        }

        function cancelSubscriptionFeedback() {
            $("#cancel-subscription-modal").modal("hide");
            $("#cancel-subscription-feedback-modal").modal("show");
            $("#feedback").focus();
        }

        function giveChance() {
            var $btn = $("#chance-button");
            $btn.attr("disabled", "disabled");
            $.post("${request.route_path('json.cancel_subscription')}", {
                "${url_param_feedback}": $("#feedback").val(),
                "${url_param_chance}": 1
            }).done(function () {
                $("#cancel-subscription-feedback-modal").modal("hide");
                $("#chance-given-modal").modal("show");
            }).fail(function(xhr) {
                showErrorMessageFromResponse(xhr);
            }).always(function() {
                $btn.removeAttr("disabled");
            });
        }

        function executeCancellation() {
            var $btn = $("#cancel-button");
            $btn.attr("disabled", "disabled");
            $.post("${request.route_path('json.cancel_subscription')}", {
                "${url_param_feedback}": $("#feedback").val()
            }).done(function () {
                $("#cancel-subscription-feedback-modal").modal("hide");
                $("#subscription-cancelled-modal").modal("show");
            }).fail(function(xhr) {
                showErrorMessageFromResponse(xhr);
            }).always(function() {
                $btn.removeAttr("disabled");
            });
        }

    </script>
</%block>
