<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="common" file="common.mako"/>

<%
    authenticator = current_config['lib.authenticator']
    # default is local credential if the system is uninitialized
    local = authenticator == '' or authenticator == 'local_credential'
%>

############################################
## Names of all the input fields in this file are identical to the names of
## their corresponding external properties (as oppose to template properties).
############################################

<form id="id-form" method="POST">
    ${csrf.token_input()}

    <h4>Managing accounts:</h4>

    <label class="radio">
        <input type='radio' name='authenticator' value='local_credential'
               onchange="localSelected()"
           %if local:
               checked
           %endif
        >
        Use AeroFS to manage user accounts

        %if not is_configuration_initialized:
            <div class="main-option-footnote">Choose this option if you're not sure.
                You'll be able to change it later at any time. All your users
                will migrate automatically.</div>
        %endif
    </label>

    <label class="radio">
        <input type='radio' name='authenticator' value='external_credential'
               onchange="ldapSelected()"
            %if not local:
               checked
            %endif
        >
        Use ActiveDirectory or LDAP
    </label>

    ## The slide down options
    <div id="ldap-options"
        %if local:
            class="hide"
        %endif
    >
        ${ldap_options()}
    </div>

    <hr />
    ${common.render_previous_button(page)}
    ${common.render_next_button("submitForm()")}
</form>

<%def name="ldap_options()">
    <div class="row-fluid">
        <div class="span8">
            <label for="ldap-server-host">Server host:</label>
            <input class="ldap-opt input-block-level" id="ldap-server-host"
                    name="ldap_server_host" type="text" required
                    value="${current_config['ldap.server.host']}">
            <div class="input-footnote">example: <i>ldap.example.com</i></div>
        </div>
        <div class="span4">
            <label for="ldap-server-port">Server port:</label>
            <input class="ldap-opt input-block-level" id="ldap-server-port"
                   name="ldap_server_port" type="text" required
                   value="${current_config['ldap.server.port']}">
            <div class="input-footnote">example: <i>389</i>, or <i>636</i> for SSL</div>
        </div>
    </div>

    <label for="ldap-server-schema-user-base">Base DN:</label>
    <input class="ldap-opt input-block-level" id="ldap-server-schema-user-base"
           name="ldap_server_schema_user_base" type="text" required
           value="${current_config['ldap.server.schema.user.base']}">
    <div class="input-footnote">example: <i>cn=users,dc=example,dc=com</i></div>

    <div class="row-fluid">
        <div class="span6">
            <label for="ldap-server-principal">Bind user name:</label>
            <input class="ldap-opt input-block-level" id="ldap-server-principal"
                   name="ldap_server_principal" type="text" required
                   value="${current_config['ldap.server.principal']}">
            <div class="input-footnote">example: <i>cn=admin,ou=users,dc=example,dc=com</i></div>
        </div>
        <div class="span6">
            <label for="ldap-server-credential">Password:</label>
            <input class="ldap-opt input-block-level" id="ldap-server-credential"
                   name="ldap_server_credential" type="password" required
                   value="${current_config['ldap.server.credential']}">
            <div class="input-footnote">password for the bind user</div>
        </div>
    </div>

    <% security = current_config['ldap.server.security'] %>

    <label>Security:</label>
    <label class="radio inline">
        <input class="ldap-opt" type="radio" name="ldap_server_security" value="none"
            %if security == 'none':
                checked
            %endif
        > Plaintext
    </label>
    <label class="radio inline">
        <input class="ldap-opt" type="radio" name="ldap_server_security" value="tls"
            ## Use TLS by default
            %if security == '' or security == 'tls':
                checked
            %endif
        > StartTLS
    </label>
    <label class="radio inline">
        <input class="ldap-opt" type="radio" name="ldap_server_security" value="ssl"
            %if security == 'ssl':
                checked
            %endif
        > SSL
    </label>

    <p style="margin-top: 20px;">
        <a id="show-advanced-ldap-options" href="#"
            onclick="showAdvancedLDAPOptions(); return false;">
            Show advanced options &#x25BE;</a>
        <a id="hide-advanced-ldap-options" href="#" class="hide"
            onclick="hideAdvancedLDAPOptions(); return true;">
            Hide advanced options &#x25B4;</a>
    </p>

    <div id="advanced-ldap-options" class="hide">
    <hr />
        ${advanced_ldap_options()}
    </div>
</%def>

<%def name="advanced_ldap_options()">
    <% scope = current_config['ldap.server.schema.user.scope'] %>
    <label>Search scope:</label>
    <label class="radio">
        <input class="ldap-opt" type="radio" name="ldap_server_schema_user_scope" value="subtree"
            %if scope =='' or scope == 'subtree':
                checked
            %endif
        > Search the object specified by Base DN <i>and</i> its entire subtree
    </label>
    <label class="radio">
        <input class="ldap-opt" type="radio" name="ldap_server_schema_user_scope" value="one"
            %if scope == 'one':
                checked
            %endif
        > Search the immediate children of Base DN, but not Base DN itself
    </label>
    <label class="radio">
        <input class="ldap-opt" type="radio" name="ldap_server_schema_user_scope" value="base"
            %if scope == 'base':
                checked
            %endif
        > Search Base DN only
    </label>

    <div class="row-fluid" style="margin-top: 20px;">
        <div class="span6">
            <%
                default = 'givenName'
                value = current_config['ldap.server.schema.user.field.firstname']
                if not value: value = default
            %>
            <label for="ldap-server-schema-user-field-firstname">First name attribute:</label>
            <input class="ldap-opt input-block-level" id="ldap-server-schema-user-field-firstname"
                   name="ldap_server_schema_user_field_firstname" type="text" required
                   value="${value}">
            <div class="input-footnote">LDAP attribute for users' first names.
                default is <i>${default}</i></div>
        </div>
        <div class="span6">
            <%
                default = 'sn'
                value = current_config['ldap.server.schema.user.field.lastname']
                if not value: value = default
            %>
            <label for="ldap-server-schema-user-field-lastname">Last name attribute:</label>
            <input class="ldap-opt input-block-level" id="ldap-server-schema-user-field-lastname"
                   name="ldap_server_schema_user_field_lastname" type="text" required
                   value="${value}">
            <div class="input-footnote">LDAP attribute for users' last names.
                default is <i>${default}</i></div>
        </div>
    </div>

    <div class="row-fluid">
        <div class="span6">
            <%
                default = 'mail'
                value = current_config['ldap.server.schema.user.field.email']
                if not value: value = default
            %>
            <label for="ldap-server-schema-user-field-email">Email attribute:</label>
            <input class="ldap-opt input-block-level" id="ldap-server-schema-user-field-email"
                   name="ldap_server_schema_user_field_email" type="text" required
                   value="${value}">
            <div class="input-footnote">LDAP attribute for users' email.
                default is <i>${default}</i></div>
        </div>
        <div class="span6">
            <%
                default = 'organizationalPerson'
                value = current_config['ldap.server.schema.user.class']
                if not value: value = default
            %>
            <label for="ldap-server-schema-user-class">User class:</label>
            <input class="ldap-opt input-block-level" id="ldap-server-schema-user-class"
                   name="ldap_server_schema_user_class" type="text" required
                   value="${value}">
            <div class="input-footnote">The object class all user records should belong to.
                default is <i>${default}</i></div>
        </div>
    </div>

    <%
        default = 'dn'
        value = current_config['ldap.server.schema.user.field.rdn']
        if not value: value = default
    %>
    <label for="ldap-server-schema-user-field-rdn">Distinguished name attribute:</label>
    <input class="ldap-opt input-block-level" id="ldap-server-schema-user-field-rdn"
           name="ldap_server_schema_user_field_rdn" type="text" required
           value="${value}">
    <div class="input-footnote">The attribute that contains users' Relative
        Distinguished Names (RDN) that will be used for authentication.
        This should be an attribute that returns an LDAPidentifier like
        "CN=User,OU=People,DC=example,DC=com".
        default is <i>${default}</i></div>

    <label for="ldap-server-ca_certificate">Server certificate for StartTLS and SSL (optional):</label>
    <textarea rows="4" class="ldap-opt input-block-level" id="ldap-server-ca_certificate"
            name="ldap_server_ca_certificate"
            ## Don't leave spaces around the config value; otherwise they will
            ## show up in the box.
            ## the .replace() converts the cert from properties format to HTML format.
            ## Also see setup.py:_format_pem() for the reversed convertion.
            >${current_config['ldap.server.ca_certificate'].replace('\\n', '&#13;&#10;')}</textarea>
    <div class="input-footnote">Supply the LDAP server's certificate here only
        if the certificate is not publicly signed.</div>

</%def>

<script>
    function localSelected() {
        $('#ldap-options').hide();
    }

    function ldapSelected() {
        $('#ldap-options').show();
    }

    function showAdvancedLDAPOptions() {
        $('#advanced-ldap-options').show();
        $('#show-advanced-ldap-options').hide();
        $('#hide-advanced-ldap-options').show();
    }

    function hideAdvancedLDAPOptions() {
        $('#advanced-ldap-options').hide();
        $('#show-advanced-ldap-options').show();
        $('#hide-advanced-ldap-options').hide();
    }

    function submitForm() {
        disableButtons();
        var authenticator = $(':input[name=lib.authenticator]:checked').val();
        if (authenticator == 'local_credential') {
            post(gotoNextPage, enableButtons);
        }
        else validateAndSubmitLDAPForm(gotoNextPage, enableButtons);
    }

    function submitLocalAuthenticatorForm(done, error) {
        console.log("local");
    }

    function validateAndSubmitLDAPForm(done, error) {
        if (!validateLDAPForm()) error();
        else submitLDAPForm(done, error);
    }

    function submitLDAPForm(done, error) {
        post(done, error);
    }

    function post(done, error) {
        ## Trim the cert
        var $cert = $('#ldap-server-ca_certificate');
        $cert.val($.trim($cert.val()));

        $.post('${request.route_path('json_setup_identity')}',
                $('#id-form').serialize())
        .done(done)
        .error(function (xhr) {
            showErrorMessageFromResponse(xhr);
            error();
        });
    }

    ## return false if the form is valid, and the error message has been displayed.
    function validateLDAPForm() {
        var hasEmptyRequiredField = false;
        $('.ldap-opt[required]').each(function() {
            if (!$(this).val()) hasEmptyRequiredField = true;
        });

        if (hasEmptyRequiredField) {
            showErrorMessage("Please fill all the fields before proceeding.");
            return false;
        }

        return true;
    }
</script>
