<%inherit file="base_layout.mako"/>
<%! page_title = "Authorize app" %>

<div class="text-center">
    ## Don't allow navigate off this page
    <%def name="home_url()">#</%def>

    <h1>Authorize ${client_name}</h1>

    <p>${client_name} is requesting access to your AeroFS folder.</p>

    <p>
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
        </form>
        <a href="#">Cancel</a>
    </p>
</div>
