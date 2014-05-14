<%inherit file="../maintenance_layout.mako"/>
<%! page_title = "Register an App" %>

<h2>Register an application</h2>

<div class="row">
    <div class="col-sm-12">
        <form method="post" class="form-horizontal page-block">
            ${self.csrf.token_input()}

            <div class="form-group">
                <label class="control-label col-sm-2" for="client_name">Name:</label>
                <div class="col-sm-6">
                    <input type="text" class="form-control" id="client_name" name="client_name" required>
                </div>
            </div>
            <div class="form-group">
                <label class="control-label col-sm-2" for="redirect_uri">Redirect URI:</label>
                <div class="col-sm-6">
                    <input type="text" class="form-control" id="redirect_uri" name="redirect_uri" required>
                </div>
            </div>
            <div class="form-group">
                <div class="col-sm-6 col-sm-offset-2">
                    <button class="btn btn-primary">Register</button>
                </div>
            </div>
        </form>
    </div>
</div>

<%block name="scripts">
    <script>
        $(document).ready(function() {
            $('#client_name').focus();
        });
    </script>
</%block>
