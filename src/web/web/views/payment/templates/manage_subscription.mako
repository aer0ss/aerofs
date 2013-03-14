<%inherit file="layout.mako"/>
<%! navigation_bars = True; %>

## Without the page_block div, the heading and the form has less spacing than
## other pages. I didn't have time to investigate why.

<div class="page_block" xmlns="http://www.w3.org/1999/html">
    <h2>Manage Subscription</h2>
</div>

<div class="page_block">
    %if trialing:
        <p>
            Your free trial will end in <strong>${trial_days_left}</strong> days.
        </p>
    %else:
        <p>You are paying (in US dollars):</p>

        <style type="text/css">
            .symbol {
                margin: auto 12px;
            }
        </style>

        ## The container is to keep the table in its own row
        <p>
            <span class="lead"><strong>${user_count}</strong></span>
                %if user_count == 1:
                    user
                %else:
                    users
                %endif
            <span class="lead symbol">&#x00D7;</span>
            <span class="lead"><strong>$${unit_price_dollars}</strong></span> /month
            <span class="lead symbol">=</span>
            <span class="lead"><strong>$${user_count * unit_price_dollars}</strong></span> /month
        </p>

        <p>
            You can add or remove users on the <a href="${request.route_path('admin_users')}">Members</a>
            page.
        </p>
    %endif
</div>

<a id="cancel_btn" class="btn" href="#">Cancel Subscription</a>

<div id="cancel_modal" class="modal hide" tabindex="-1" role="dialog">
    <div class="modal-header">
        <button type="button" class="close" data-dismiss="modal">×</button>
        <h3>Cancel subscription</h3>
    </div>
    <div class="modal-body">
        <p>Are you sure you want to cancel your subscription?</p>

        <p class="footnote">Canceling removes all members from your team
            and their files will no longer sync with AeroFS Team Servers.</p>
    </div>
    <div class="modal-footer">
        <form id="cancel_form" class="form-inline"
                action="${request.route_path('cancel_subscription')}" method="post"
                style="margin-bottom: 0;">
            ${self.csrf_token_input()}
            <a class="btn" data-dismiss="modal">No. This is not what I want</a>
            <input type="submit" class="btn btn-danger" value="Yes. Cancel subscription">
        </form>
    </div>
</div>

<%block name="scripts">
    <script type="text/javascript">
        $(document).ready(function() {
            $('#cancel_btn').click(function() {
                $('#cancel_modal').modal('show');
            });

            mixpanel.track_forms("#cancel_form", "Cancelled Business Plan");
        });
    </script>
</%block>
