<%inherit file="layout.mako"/>
<%! navigation_bars = True; %>

<%namespace name="credit_card_modal" file="credit_card_modal.mako"/>

<div class="page_block">
    <h2>Current Subscription</h2>

    %if quantity == 0:
        ${free_plan()}
    %else:
        ${paid_plan()}
    %endif

    <p>
        The system will automatically adjust the subscription when you
        <a href="${request.route_path('team_members')}">add or remove members.</a><br>
        You can review pricing plans
        <a href="https://www.aerofs.com/pricing" target="_blank">here</a>.
    </p>
</div>

<div class="page_block">
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

<div class="page_block">
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

<%def name="free_plan()">
    <p>You are using AeroFS <strong>for free</strong>. No subscription is required. Yay!</p>
</%def>

<%def name="paid_plan()">
    <style type="text/css">
        .symbol {
            margin: auto 12px;
        }
    </style>

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
</%def>

<%credit_card_modal:html>
    <%def name="title()">
        Edit Payment Method
    </%def>
    <%def name="description()">
        <p>
            Please enter the new payment method below:
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
        function updateCreditCard(token, done, fail) {
            $.post("${request.route_path('json.update_credit_card')}", {
                ${self.csrf.token_param()}
                "${url_param_stripe_card_token}": token
            })
            .done(function() {
                ## Reload the page to refresh the current payment method.
                ## Do not call done() so the payment dialog will stay until
                ## the page is fully reloaded.
                ##
                ## TODO (WW) also show a successful message?
                window.location.href = '${request.url}';
            })
            .fail(function(xhr) {
                showErrorMessageFromResponse(xhr);
                fail();
            });
        }
    </script>
</%block>