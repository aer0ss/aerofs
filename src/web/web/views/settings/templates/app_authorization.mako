<%inherit file="base_layout.mako"/>
<%! page_title = "Authorize app" %>

<div class="text-center">
    ## Don't allow navigate off this page
    <%def name="home_url()">#</%def>

    <h2 class="page-block">Authorize "${client_name}"</h2>

    <p class="page-block">The app <strong>${client_name}</strong> is requesting
        read and write access to your AeroFS folder.</p>

    <p style="margin-top: 60px">
        <form method="post" action="auth/authorize" method="post">
            ${self.csrf.token_input()}
            <input type="hidden" name="response_type" value="${response_type}"/>
            <input type="hidden" name="client_id" value="${client_id}"/>
            <input type="hidden" name="nonce" value="${identity_nonce}"/>
            <input type="hidden" name="redirect_uri" value="${redirect_uri}"/>
            % if state is not None:
                <input type="hidden" name="state" value="${state}"/>
            % endif
            <button class="btn btn-primary">Accept</button>
            <a class="btn btn-link" href="${request.route_path('access_tokens')}">Cancel</a>
        </form>
    </p>
</div>
