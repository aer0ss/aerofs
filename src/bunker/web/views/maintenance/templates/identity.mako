<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Identity" %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="bootstrap" file="bootstrap.mako"/>
<%namespace name="modal" file="modal.mako"/>
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>

<div class="page-block">
    <h2>Identity management</h2>

    <p>You may choose AeroFS or a 3rd-party identity provider to manage user accounts. Switching between them has
        minimal disruption to your user base. <a href="https://support.aerofs.com/entries/23544130" target="_blank">
        Learn more</a>.</p>
</div>

<%
    authenticator = conf['lib.authenticator']
    # Use the local namespace so the method scripts() can access it
    local.local_auth = authenticator == 'local_credential'
%>

############################################
## Names of all the input fields in this file are identical to the names of
## their corresponding external properties (as oppose to template properties).
############################################

<form class="page-block" method="POST" onsubmit="submitForm(); return false;">
    ${csrf.token_input()}

    <label class="radio">
        <input type='radio' name='authenticator' value='local_credential'
               onchange="localSelected()"
           %if local.local_auth:
               checked
           %endif
        >
        Use AeroFS to manage user accounts
    </label>

    <label class="radio">
        <input type='radio' name='authenticator' value='external_credential'
               onchange="ldapSelected()"
            %if not local.local_auth:
               checked
            %endif
        >
        Use ActiveDirectory or LDAP

        ## The slide down options
        <div id="ldap-options"
            %if local.local_auth:
                class="hide"
            %endif
        >
            <p style="margin-top: 6px"><a href="https://support.aerofs.com/entries/23101219" target="_blank">
                    Need help setting up or troubleshooting AD/LDAP?</a></p>

            ${ldap_options()}
        </div>
    </label>

    <hr />
    <button id="save-btn" class="btn btn-primary">Save</button>
</form>

########
## N.B. the name of all LDAP specific options must start with "ldap_".
## This is required by identity_view.py:_get_ldap_specific_options().
########

<%def name="ldap_options()">
    <div class="row-fluid">
        <div class="span8">
            <label for="ldap-server-host">Server host:</label>
            <input class="ldap-opt input-block-level" id="ldap-server-host"
                    name="ldap_server_host" type="text" required
                    value="${conf['ldap.server.host']}">
            <div class="input-footnote">example: <i>ldap.example.com</i></div>
        </div>
        <div class="span4">
            <label for="ldap-server-port">Server port:</label>
            <input class="ldap-opt input-block-level" id="ldap-server-port"
                   name="ldap_server_port" type="text" required
                   value="${conf['ldap.server.port']}">
            <div class="input-footnote">example: <i>389</i>, or <i>636</i> for SSL</div>
        </div>
    </div>

    <label for="ldap-server-schema-user-base">Base DN:</label>
    <input class="ldap-opt input-block-level" id="ldap-server-schema-user-base"
           name="ldap_server_schema_user_base" type="text" required
           value="${conf['ldap.server.schema.user.base']}">
    <div class="input-footnote">example: <i>cn=users,dc=example,dc=com</i></div>

    <div class="row-fluid">
        <div class="span6">
            <label for="ldap-server-principal">Bind user name:</label>
            <input class="ldap-opt input-block-level" id="ldap-server-principal"
                   name="ldap_server_principal" type="text" required
                   value="${conf['ldap.server.principal']}">
            <div class="input-footnote">example: <i>cn=admin,ou=users,dc=example,dc=com</i></div>
        </div>
        <div class="span6">
            <label for="ldap-server-credential">Password:</label>
            <input class="ldap-opt input-block-level" id="ldap-server-credential"
                   name="ldap_server_credential" type="password" required
                   value="${conf['ldap.server.credential']}">
            <div class="input-footnote">password for the bind user</div>
        </div>
    </div>

    <% security = conf['ldap.server.security'] %>

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
    <% scope = conf['ldap.server.schema.user.scope'] %>
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
                value = conf['ldap.server.schema.user.field.firstname']
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
                value = conf['ldap.server.schema.user.field.lastname']
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
                value = conf['ldap.server.schema.user.field.email']
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
                value = conf['ldap.server.schema.user.class']
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
        value = conf['ldap.server.schema.user.field.rdn']
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
            ## Also see setup_view.py:_format_pem() for the reversed convertion.
            >${conf['ldap.server.ca_certificate'].replace('\\n', '\n')}</textarea>
    <div class="input-footnote">Supply the LDAP server's certificate only
        if the certificate is <strong>not</strong> publicly signed.</div>
</%def>

<%modal:modal>
    <%def name="id()">success-modal</%def>
    <%def name="title()">You are almost done</%def>

    <p>The configuration is saved. However, to complete switching to the new identity system,
        please <a href="https://support.aerofs.com/entries/23544130" target="_blank">read
        this article</a> and instruct users to update their passwords as necessary.</p>

    <%def name="footer()">
        <a href="#" data-dismiss="modal" class="btn btn-primary">Got It</a>
    </%def>
</%modal:modal>

<%progress_modal:html>
    Please wait while we apply changes...
</%progress_modal:html>

<%block name="scripts">
    <%bootstrap:scripts/>
    <%progress_modal:scripts/>
    ## spinner support is required by progress_modal
    <%spinner:scripts/>

    <script>
        var ldapOptionChanged = false;
        $(document).ready(function() {
            initializeProgressModal();

            %if local.local_auth:
                localSelected();
            %else:
                ldapSelected();
            %endif

            ## Listen to any changes to LDAP options.
            $('.ldap-opt').change(function () {
                ldapOptionChanged = true;
            })
        });

        function localSelected() {
            ## Remove 'required' attribute otherwise Chrome would complain when
            ## submitting the form since required fields are invisible. See:
            ## http://stackoverflow.com/questions/7168645/invalid-form-control-only-in-google-chrome
            ## Adding the ldap-required class is for ldapSelected() to restore the
            ## attribute.
            $('.ldap-opt[required]').removeAttr('required').addClass('ldap-required');
            $('#ldap-options').hide();
        }

        function ldapSelected() {
            $('.ldap-required').attr('required', 'required');
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
            var $progressModal = $('#${progress_modal.id()}');
            $progressModal.modal('show');
            var always = function() {
                $progressModal.modal('hide');
            };

            var authenticator = $(':input[name=lib.authenticator]:checked').val();
            if (authenticator == 'local_credential') {
                post(always);
            } else {
                validateAndSubmitLDAPForm(always);
            }
        }

        function validateAndSubmitLDAPForm(always) {
            if (!validateLDAPForm(always)) return;

            ## Verify LDAP if any LDAP options have changed or the user switches
            ## from local auth to LDAP.
            var wasLocalAuth = ${str(local.local_auth).lower()};
            if (ldapOptionChanged || wasLocalAuth) {
                console.log("ldap opt changed. test new opts");

                $.post('${request.route_path('json_verify_ldap')}',
                        $('form').serialize())
                .done(function () {
                    post(always);
                })
                .error(function (xhr) {
                    showErrorMessageFromResponse(xhr);
                    always();
                });
            } else {
                ## This post is required to write changes in _other_ option values,
                ## even if ldap-specific options are not changed.
                post(always);
            }
        }

        function post(always) {
            ## Trim the cert
            var $cert = $('#ldap-server-ca_certificate');
            $cert.val($.trim($cert.val()));

            $.post('${request.route_path('json_set_identity_options')}',
                    $('form').serialize())
            .done(function() {
                restartServices(always);
            })
            .error(function (xhr) {
                showErrorMessageFromResponse(xhr);
                always();
            });
        }

        ## return false if the form is valid, and the error message has been displayed.
        function validateLDAPForm(always) {
            var hasEmptyRequiredField = false;
            $('.ldap-opt[required]').each(function() {
                if (!$(this).val()) hasEmptyRequiredField = true;
            });

            if (hasEmptyRequiredField) {
                showErrorMessage("Please fill in all required fields.");
                always();
                return false;
            }

            return true;
        }

        function restartServices(always) {
            runBootstrapTask('restart-services-for-identity',
                ## done
                function() {
                    var $successModal = $('#success-modal');
                    $successModal.modal('show');
                    $successModal.on('hidden', function() {
                        ## this page has states, and the states are set when
                        ## the app server generates the page. The state may
                        ## change after we restart the service, so we ask the
                        ## app server to generate this page again to ensure the
                        ## page has the correct state after the change.
                        window.location.assign('${request.route_path('identity')}');
                    });
                    always();
                },
                ## fail
                always);
        }
    </script>
</%block>
