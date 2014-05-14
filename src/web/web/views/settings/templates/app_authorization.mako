<%inherit file="base_layout.mako"/>
<%! page_title = "Authorize app" %>

<%block name="scripts">
    <script type="text/javascript">
        var SCOPES = [
            {"name": "organization.admin", "qualified": false, "description": "Take administrative actions, as well as any action below, on behalf of other users in your organization."},
            {"name": "user.read", "qualified": false, "description": "View your name and email address."},
            {"name": "user.write", "qualified": false, "description": "Update your name."},
            {"name": "user.password", "qualified": false, "description": "Reset and modify your password."},
            {"name": "files.read", "qualified": true, "description": "List folders and download files."},
            {"name": "files.write", "qualified": true, "description": "Create, modify, and delete files and folders."},
            {"name": "files.appdata", "qualified": false, "description": "Read and write files in a sandboxed location."},
            {"name": "acl.invitations", "qualified": false, "description": "List, accept, and ignore shared folder invitations."},
            {"name": "acl.read", "qualified": true, "description": "List shared folders and their members."},
            {"name": "acl.write", "qualified": true, "description": "Create and manage shared folders."},
        ];

        var SHARES = ${shares|n};
        var requested_scopes = ${scopes|n};

        function grantedScope(name) {
            if (requested_scopes[name].length > 0) {
                var joined = "";
                $.each(requested_scopes[name], function (idx, qual) {
                    if (joined.length > 0) joined += ",";
                    joined += name;
                    joined += ":";
                    joined += qual;
                });
                return joined;
            }
            return name;
        }

        function updateScope() {
            var raw_scopes = "";
            $.each(SCOPES, function(idx, scope) {
                if (scope.name in requested_scopes) {
                    var granted = grantedScope(scope.name);
                    if (granted.length > 0) {
                        if (raw_scopes.length > 0) raw_scopes += ",";
                        raw_scopes += granted;
                    }
                }
            });
            $("#granted_scope").val(raw_scopes);
        }

        function addRequestedScope(scope, scope_quals) {
            var id = scope.name.replace('.', '_');

            $("<div><ul></ul></div>")
                .append($("<li style=\"margin-top: 20px\"></li>")
                    .attr("id", "title_" + id)
                    .addClass("scope")
                    .text(scope.description))
                .appendTo($("#scopes"));
        }

        $.each(SCOPES, function (idx, scope) {
            if (scope.name in requested_scopes) {
                addRequestedScope(scope, requested_scopes[scope.name]);
            }
        });

        updateScope();
    </script>
</%block>

<div class="row">
    <div class="col-sm-8 col-sm-offset-2">
        ## Don't allow navigate off this page
        <%def name="home_url()">#</%def>

        <h2 class="page-block">Authorize "${client_name}"</h2>

        <p class="page-block">Please review the requested permissions below.</p>

        <div class="page-block">
        <span><strong>${client_name}</strong> wants to:</span>
        </div>

        <div id="scopes">
        </div>

        <hr/>

        <p style="margin-top: 20px">
            <form method="post" action="auth/authorize" method="post">
                ${self.csrf.token_input()}
                <input type="hidden" name="response_type" value="${response_type}"/>
                <input type="hidden" name="client_id" value="${client_id}"/>
                <input type="hidden" name="nonce" value="${identity_nonce}"/>
                <input type="hidden" name="redirect_uri" value="${redirect_uri}"/>
                <input type="hidden" id="granted_scope" name="scope" value=""/>
                % if state is not None:
                    <input type="hidden" name="state" value="${state}"/>
                % endif
                <button class="btn btn-primary">Accept</button>
                <a class="btn btn-link" href="${request.route_path('access_tokens')}">Cancel</a>
            </form>
        </p>
    </div>
</div>
