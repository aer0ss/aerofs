<%inherit file="marketing_layout.mako"/>
<%! page_title = "Two-factor Authentication" %>

<div class="row">
    <div class="col-sm-4 col-sm-offset-4 login">
        <h1>Two-factor Authentication</h1>

        <form id="signin_form" class="form-horizontal" role="form" action="${request.url}" method="post">
            ${self.csrf.token_input()}
            <div class="form-group">
                <label for="input_auth_code" class="col-sm-4 control-label">Authentication Code</label>
                <div class="col-sm-8">
                    <input class="input-medium form-control" id="input_auth_code" type="text" maxlength="6" name="${url_param_code}"/>
                </div>
            </div>
            <div class="form-group">
                <div class="col-sm-8 col-sm-offset-4">
                    <input id="signin_button" class="btn btn-primary" type="submit" value="Verify"/>
                </div>
            </div>
        </form>

        <div class="row">
            <div class="col-sm-8 col-sm-offset-4"><a href="${request.route_path('login_backup_code')}">Don't have your phone?</a></div>
        </div>
    </div>
</div>

<%block name="scripts">
    <script type="text/javascript">
        $(document).ready(function() {
            $('#input_auth_code').focus();

            $('#signin_form').submit(function() {
                $("#signin_button").attr("disabled", "disabled");
                return true;
            });
        });
    </script>
</%block>
