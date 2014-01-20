<%inherit file="../maintenance_layout.mako"/>
<%! page_title = "Register an App" %>

<h2 class="page-block">Register an application</h2>

<form method="post" class="form-horizontal page-block">
    ${self.csrf.token_input()}

    <div class="control-group">
        <label class="control-label" for="client_name">Name:</label>
        <div class="controls">
            <input type="text" class="input-xlarge" id="client_name" name="client_name" required>
        </div>
    </div>
    <div class="control-group">
        <label class="control-label" for="redirect_uri">Redirect URI:</label>
        <div class="controls">
            <input type="text" class="input-xlarge" id="redirect_uri" name="redirect_uri" required>
        </div>
    </div>
    <div class="control-group">
        <div class="controls">
            <button class="btn btn-primary">Register</button>
        </div>
    </div>
</form>

<%block name="scripts">
    <script>
        $(document).ready(function() {
            $('#client_name').focus();
        });
    </script>
</%block>
