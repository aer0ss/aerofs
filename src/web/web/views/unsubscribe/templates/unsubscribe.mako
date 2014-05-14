<%inherit file="marketing_layout.mako"/>
<%! page_title = "Unsubscribe Email" %>

<div class="row">
    <div class="col-sm-6 col-sm-offset-3">
        <h2>AeroFS Unsubscribe Request</h2>

        %if success:
            <div id="success_msg">
                Your email (${email}) has been successfully unsubscribed. Have a great day!
            </div>
        %else:
            <div id="bad_token_error">
                An error occurred while trying to unsubscribe your email.  Please try again.
                If you continue to have problems, please contact
                <a href="http://support.aerofs.com">support</a>.
            </div>
        %endif
    </div>
</div>