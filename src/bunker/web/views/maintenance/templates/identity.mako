<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Identity" %>

<%!
    from web.views.maintenance.maintenance_util import unformat_pem
    from web.util import str2bool
%>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="loader" file="loader.mako"/>
<%namespace name="modal" file="modal.mako"/>
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>

<%
    authenticator = conf['lib.authenticator']
    if conf['signup_restriction'] == "":
        signup_restriction = 'USER_INVITED'
    else:
        signup_restriction = conf['signup_restriction']
    # Use the local namespace so the method scripts() can access it
    local.local_auth = authenticator == 'local_credential'
    local.external_auth = authenticator == 'external_credential'
    local.saml_auth = authenticator == 'SAML'
    local.ldap_group_sync_schedule_enum = conf['ldap.groupsyncing.schedule_enum']
%>

<div class="page-block">
    <h2>New User Invitations</h2>
</div>

<form id="signup-restriction-form" method="POST" onsubmit="submitSignupRestrictionForm(); return false;">
    ${csrf.token_input()}
    <div class="row">
        <div class="col-sm-12">
            <div class="radio">
                <label>
                    <input type='radio' name='signup_restriction' value='UNRESTRICTED'
                        %if signup_restriction == 'UNRESTRICTED':
                           checked
                        %endif
                    >
                    New users can signup on their own at <b><a href="https://${conf['base.host.unified']}"
                        target="_blank" style="color:#00A5C1">
                        https://${conf['base.host.unified']}</a></b>
                </label>
            </div>

            <div class="radio">
                <label>
                    <input type='radio' name='signup_restriction' value='USER_INVITED'
                        %if signup_restriction == 'USER_INVITED':
                            checked
                        %endif
                    >
                    Only existing AeroFS users can invite new users
                </label>
            </div>

            <div class="radio">
                <label>
                    <input type='radio' name='signup_restriction' value='ADMIN_INVITED'
                        %if signup_restriction == 'ADMIN_INVITED':
                           checked
                        %endif
                    >
                    Only AeroFS administrators can invite new users
                </label>
            </div>

            <button type="submit" id="save-signup-restriction" class="btn btn-primary">Save</button>
        </div>
    </div>
</form>

<hr>

<div class="page-block">
    <h2>Identity Management</h2>

    <p>You may choose AeroFS or a 3rd-party identity provider to manage user accounts. Switching between them has
        minimal disruption to your user base. <a href="https://support.aerofs.com/hc/en-us/articles/204592834" target="_blank">
        Learn more</a>.</p>
</div>

############################################
## Names of all the input fields in this file are identical to the names of
## their corresponding external properties (as opposed to template properties).
############################################

<form id="identity-form" method="POST" onsubmit="submitForm(); return false;">
    ${csrf.token_input()}

    <div class="row">
        <div class="col-sm-12">


            <div class="radio">
                <label>
                    <input type='radio' name='authenticator' value='local_credential'
                           onchange="localSelected()"
                       %if local.local_auth:
                           checked
                       %endif
                    >
                    Use AeroFS to manage user accounts
                </label>
            </div>

            <div class="radio">
                <label>
                    <input type='radio' name='authenticator' value='external_credential'
                           onchange="ldapSelected()"
                        %if local.external_auth:
                           checked
                        %endif
                    >
                    Use ActiveDirectory or LDAP
                </label>
            </div>

            <div class="radio">

                <label>
                    <input type='radio' name='authenticator' value='SAML'
                           onchange="samlSelected()"
                        %if local.saml_auth:
                           checked
                        %endif
                    >
                    Use SAML
                </label>
            </div>

                ## The slide down options
                <div id="ldap-options"
                    %if local.local_auth or local.saml_auth:
                        style="display: none;"
                    %endif
                >
                    <p><a href="https://support.aerofs.com/hc/en-us/articles/204861930" target="_blank">
                            Need help setting up or troubleshooting AD/LDAP?</a></p>

                    ${ldap_options()}
                </div>

                <div id="saml-options"
                    %if local.local_auth or local.external_auth:
                        style="display: none;"
                    %endif
                >
                    ${saml_options()}
                </div>
            </div>

            <button type="submit" id="save-ldap" class="btn btn-primary">Save</button>
        </div>
    </div>
</form>


########
## N.B. the name of all SAML specific options must start with "saml_".
## This is required by identity_view.py:_get_saml_specific_options().
########
<%def name="saml_options()">
    <div class="row">
        <div class="col-sm-6">
            <label for="identity-service-identifier" class="control-label">SAML IDP Identifier(Optional)</label>
            <input class="form-control" id="identity-service-identifier"
                    name="saml_identity_service_identifier" type="text" optional
                    value="${conf['saml.identity.service.identifier']}">
            <div class="help-block">e.g. Okta, OneLogin. Users will be asked to sign in via this identifier.</div>
        </div>
        <div class="col-sm-6">
            <label for="saml-idp-host" class="control-label">SAML IDP Single Sign On URL</label>
            <input class="form-control saml-required" id="saml-idp-host"
                    name="saml_idp_host" type="text"
                    value="${conf['saml.idp.host']}">
            <div class="help-block">e.g. saml.example.com/sso/saml</div>
        </div>
    </div>

    <div class="row">
        <div class="col-sm-12">
            <label class="control-label" for="saml-idp-x509_certificate">SAML IDP X509 Certificate</label>
            <textarea rows="4" class="form-control" id="saml-idp-x509_certificate"
                    name="saml_idp_x509_certificate"
                    ## Dont leave spaces around the config value; otherwise they will
                    ## show up in the box.
                    ## the .replace() converts the cert from properties format to HTML format.
                    ## Also see setup_view.py:_format_pem() for the reversed convertion.
                    >${unformat_pem(conf['saml.idp.x509.certificate'])}</textarea>
        </div>
    </div>
<hr />
</%def>


########
## N.B. the name of all LDAP specific options must start with "ldap_".
## This is required by identity_view.py:_get_ldap_specific_options().
########

<%def name="ldap_options()">
    <div class="row">
        <div class="col-sm-6">
            <label for="ldap-server-host" class="control-label">Server host</label>
            <input class="form-control ldap-required" id="ldap-server-host"
                    name="ldap_server_host" type="text"
                    value="${conf['ldap.server.host']}">
            <div class="help-block">e.g. ldap.example.com</div>
        </div>
        <div class="col-sm-6">
            <label for="ldap-server-port" class="control-label">Server port</label>
            <input class="form-control ldap-required" id="ldap-server-port"
                   name="ldap_server_port" type="text"
                   value="${conf['ldap.server.port']}">
            <div class="help-block">e.g. 389, or 636 for SSL</div>
        </div>
    </div>

    <div class="row">
        <div class="col-sm-6">
            <label for="ldap-server-schema-user-base" class="control-label">Base DN</label>
            <input class="form-control ldap-required" id="ldap-server-schema-user-base"
                   name="ldap_server_schema_user_base" type="text"
                   value="${conf['ldap.server.schema.user.base']}">
            <div class="help-block">e.g. <code>cn=users,dc=example,dc=com</code></div>
        </div>
    </div>
    <div class="row">
        <div class="col-sm-6">
            <label for="ldap-server-principal" class="control-label">Bind user name</label>
            <input class="form-control ldap-required" id="ldap-server-principal"
                   name="ldap_server_principal" type="text"
                   value="${conf['ldap.server.principal']}">
            <div class="help-block">e.g. <code>cn=admin,ou=users,dc=example,dc=com</code></div>
        </div>
        <div class="col-sm-6">
            <label for="ldap-server-credential" class="control-label">Bind user password</label>
            <input class="form-control ldap-required" id="ldap-server-credential"
                   name="ldap_server_credential" type="password"
                   value="${conf['ldap.server.credential']}">
        </div>
    </div>

    <% security = conf['ldap.server.security'] %>
    <div class="row">
        <div class="col-sm-6">
            <label>Security: &nbsp;</label>
            <label class="radio-inline">
                <input type="radio" name="ldap_server_security" value="none"
                    %if security == 'none':
                        checked
                    %endif
                > Plaintext
            </label>
            <label class="radio-inline">
                <input type="radio" name="ldap_server_security" value="tls"
                    ## Use TLS by default
                    %if security == '' or security == 'tls':
                        checked
                    %endif
                > StartTLS
            </label>
            <label class="radio-inline">
                <input type="radio" name="ldap_server_security" value="ssl"
                    %if security == 'ssl':
                        checked
                    %endif
                > SSL
            </label>
        </div>
    </div>
    <%
        invitation_required = str2bool(conf['ldap.invitation.required_for_signup'])
    %>
    <div class="row">
        <div class="col-sm-12">
            <label>Externally managed users must be invited to use AeroFS: &nbsp;</label>
            <label class="radio-inline">
                <input type="radio" name="ldap_invitation_required_for_signup" value="true"
                       %if invitation_required:
                           checked
                       %endif
                > Yes
            </label>
            <label class="radio-inline">
                <input type="radio" name="ldap_invitation_required_for_signup" value="false"
                       %if not invitation_required:
                           checked
                       %endif
                > No
            </label>
        </div>
    </div>
    <hr />
    <div class="row">
        <div class="col-sm-6">
            <p>
                <a id="show-advanced-ldap-options" href="#"
                    onclick="showAdvancedLDAPOptions(); return false;">
                    Show advanced options &#x25BE;</a>
                <a id="hide-advanced-ldap-options" href="#"
                    onclick="hideAdvancedLDAPOptions(); return true;" style="display: none;">
                    Hide advanced options &#x25B4;</a>
            </p>
        </div>
    </div>
    <div id="advanced-ldap-options" style="display: none;">
        <div class="col-sm-12">
            ${advanced_ldap_options()}
        </div>
    </div>
</%def>

<%def name="advanced_ldap_options()">
    <h3>Users</h3>
    <% scope = conf['ldap.server.schema.user.scope'] %>
    <div class="row">
        <div class="col-sm-12">
            <label>User search scope:</label>
            <div class="radio">
            <label>
                <input type="radio" name="ldap_server_schema_user_scope" value="subtree"
                    %if scope =='' or scope == 'subtree':
                        checked
                    %endif
                > Object specified by Base DN <em>and</em> its entire subtree
            </label>
            </div>
            <div class="radio">
            <label>
                <input type="radio" name="ldap_server_schema_user_scope" value="one"
                    %if scope == 'one':
                        checked
                    %endif
                > Immediate children of Base DN, but not Base DN itself
            </label>
            </div>
            <div class="radio">
            <label>
                <input type="radio" name="ldap_server_schema_user_scope" value="base"
                    %if scope == 'base':
                        checked
                    %endif
                > Base DN only
            </label>
            </div>
        </div>
    </div>

    <div class="row">
        <div class="col-sm-6">
            <%
                default = 'organizationalPerson'
                value = conf['ldap.server.schema.user.class']
                if not value: value = default
            %>
            <label class="control-label" for="ldap-server-schema-user-class">LDAP object class for user records</label>
            <input class="form-control ldap-required" id="ldap-server-schema-user-class"
                   name="ldap_server_schema_user_class" type="text"
                   value="${value}">
            <div class="help-block">
                Default is <code>${default}</code>.</div>
        </div>
    </div>

    <div class="row">
        <div class="col-sm-6">
            <%
                default = 'givenName'
                value = conf['ldap.server.schema.user.field.firstname']
                if not value: value = default
            %>
            <label class="control-label" for="ldap-server-schema-user-field-firstname">LDAP first name attribute</label>
            <input class="form-control ldap-required" id="ldap-server-schema-user-field-firstname"
                   name="ldap_server_schema_user_field_firstname" type="text"
                   value="${value}">
            <div class="help-block">Default is <code>${default}</code>.</div>
        </div>
        <div class="col-sm-6">
            <%
                default = 'sn'
                value = conf['ldap.server.schema.user.field.lastname']
                if not value: value = default
            %>
            <label class="control-label" for="ldap-server-schema-user-field-lastname">LDAP last name attribute</label>
            <input class="form-control ldap-required" id="ldap-server-schema-user-field-lastname"
                   name="ldap_server_schema_user_field_lastname" type="text"
                   value="${value}">
            <div class="help-block">Default is <code>${default}</code>.</div>
        </div>
    </div>

    <div class="row">
        <div class="col-sm-6">
            <%
                default = 'mail'
                value = conf['ldap.server.schema.user.field.email']
                if not value: value = default
            %>
            <label class="control-label" for="ldap-server-schema-user-field-email">LDAP email attribute</label>
            <input class="form-control ldap-required" id="ldap-server-schema-user-field-email"
                   name="ldap_server_schema_user_field_email" type="text"
                   value="${value}">
            <div class="help-block">
                Default is <code>${default}</code></div>
        </div>
    </div>

    <div class="row">
        <div class="col-sm-12">
            <%
                default = ''
                value = conf['ldap.server.schema.user.filter']
                if not value: value = default
            %>
            <label class="control-label" for="ldap-server-schema-user-filter">Additional LDAP filter criteria</label>
            <input class="form-control" id="ldap-server-schema-user-filter"
                   name="ldap_server_schema_user_filter" type="text"
                   value="${value}">
            <div class="help-block">
                An optional LDAP query fragment that will be included in the user search.
                This is not commonly used.
            </div>
        </div>
    </div>

    <div class="row">
        <div class="col-sm-12">
            <label class="control-label" for="ldap-server-ca_certificate">Server certificate for StartTLS and SSL (optional)</label>
            <textarea rows="4" class="form-control" id="ldap-server-ca_certificate"
                    name="ldap_server_ca_certificate"
                    ## Don't leave spaces around the config value; otherwise they will
                    ## show up in the box.
                    ## the .replace() converts the cert from properties format to HTML format.
                    ## Also see setup_view.py:_format_pem() for the reversed convertion.
                    >${unformat_pem(conf['ldap.server.ca_certificate'])}</textarea>
            <div class="help-block">Supply the LDAP server's certificate only
                if the certificate is <strong>not</strong> publicly signed.</div>
        </div>
    </div>

    <hr>

    <h3>Groups</h3>
    <% groups_enabled = str2bool(conf['ldap.groupsyncing.enabled']) %>
    <div class="row">
        <div class="col-sm-12">
            <div class="checkbox">
                <input type='hidden' name='ldap_groupsyncing_enabled' value='false'>
                <input type='checkbox' name='ldap_groupsyncing_enabled' value='true'
                    %if groups_enabled:
                        checked
                    %endif
                    onchange="ldapGroupsToggled(this);"
                >
                Enable LDAP Group Syncing
            </div>
        </div>
    </div>

    <div id="ldap-group-options"
            %if not groups_enabled:
                style="display: none;"
            %endif
        >
        <div class="col-sm-12">
            ${ldap_group_options()}
        </div>
    </div>
</%def>

<%def name="ldap_group_options()">
    <div class="row">
        <div class="col-sm-12">
            <%
                scope = conf['ldap.server.schema.group.scope']
                if not scope: scope = "subtree"
            %>
            <label>Group search scope:</label>
            <div class="radio">
            <label>
                <input type="radio" name="ldap_server_schema_group_scope" value="subtree"
                    %if scope == 'subtree':
                        checked
                    %endif
                > Object specified by Base DN <em>and</em> its entire subtree
            </label>
            </div>
            <div class="radio">
            <label>
                <input type="radio" name="ldap_server_schema_group_scope" value="one"
                    %if scope == 'one':
                        checked
                    %endif
                > Immediate children of, but not Base DN itself
            </label>
            </div>
            <div class="radio">
            <label>
                <input type="radio" name="ldap_server_schema_group_scope" value="base"
                    %if scope == 'base':
                        checked
                    %endif
                > Base DN only
            </label>
            </div>
        </div>
    </div>

    <div class="row">
        <div class="col-sm-6">
            <label for="ldap-server-schema-group-base" class="control-label">Base DN for groups</label>
            <input class="form-control" id="ldap-server-schema-group-base"
                   name="ldap_server_schema_group_base" type="text"
                   value="${conf['ldap.server.schema.group.base']}">
            <div class="help-block">e.g. <code>dc=roles,dc=example,dc=com</code></div>
        </div>
    </div>

    <div class="row">
        <div class="col-sm-12">
            <%
                default = 'groupOfNames groupOfUniqueNames groupOfEntries groupOfURLs groupOfUniqueURLs posixGroup'
                values = conf['ldap.server.schema.group.class']
                if not values: values = default
                values = ", ".join(values.split(separator))
            %>
            <label for="ldap-server-schema-group-class" class="control-label">LDAP Group object classes</label>
            <textarea rows="1" class="form-control" name="ldap_server_schema_group_class" id="ldap-server-schema-group-class"
                >${values}</textarea>
            <div class="help-block">Common object classes are <code>groupOfNames</code>, <code>groupOfURLs</code>,
                and <code>groupOfEntries</code>. Please separate multiple object classes with a comma.</div>
        </div>
    </div>

    <div class="row">
        <div class="col-sm-12">
            <%
                default = 'member uniqueMember'
                values = conf['ldap.server.schema.group.member.static']
                if not values: values = default
                values = ", ".join(values.split(separator))
            %>
            <label for="ldap-server-schema-group-member-static" class="control-label">LDAP Group static member attribute(s)</label>
            <textarea rows="1" class="form-control" name="ldap_server_schema_group_member_static" id="ldap-server-schema-group-member-static"
                >${values}</textarea>
            <div class="help-block">These attributes should have the DNs of group members as values,
                defaults are <code>member</code> and <code>uniqueMember</code>.
                Please separate multiple attributes with a comma.</div>
        </div>
    </div>

    <div class="row">
        <div class="col-sm-12">
            <%
                default = 'memberUrl'
                values = conf['ldap.server.schema.group.member.dynamic']
                if not values: values = default
                values = ", ".join(values.split(separator))
            %>
            <label for="ldap-server-schema-group-member-dynamic" class="control-label">LDAP Group dynamic member attribute(s)</label>
            <textarea rows="1" class="form-control" name="ldap_server_schema_group_member_dynamic" id="ldap-server-schema-group-member-dynamic"
                >${values}</textarea>
            <div class="help-block">These attributes should contain LDAP search URLs which specify members of the group,
                    default is <code>memberUrl</code>. Please separate multiple attributes with a comma.</div>
        </div>
    </div>

    <div class="row">
        <div class="col-sm-6">
            <%
                default = 'memberUid'
                value = conf['ldap.server.schema.group.member.unique']
                if not value: value = default
            %>
            <label for="ldap-server-schema-group-member-unique" class="control-label">LDAP Group unique member attribute</label>
            <input class="form-control" id="ldap-server-schema-group-member-unique"
                    name="ldap_server_schema_group_member_unique" type="text"
                    value="${value}">
            <div class="help-block">This attribute should contain values that specify group members by the value of their unique identifier attribute.
                    Default is <code>memberUid</code>.</div>
        </div>
        <div class="col-sm-6">
            <%
                default = 'uidNumber'
                value = conf['ldap.server.schema.user.field.uid']
                if not value: value = default
            %>
            <label class="control-label" for="ldap-server-schema-user-field-uid">Group member unique identifier attribute</label>
            <input class="form-control" id="ldap-server-schema-user-field-uid"
                    name="ldap_server_schema_user_field_uid" type="text"
                    value="${value}">
            <div class="help-block">Corresponds to the LDAP Group unique member attribute.
                    Default is <code>${default}</code>.</div>
        </div>
    </div>

    <div class="row">
        <div class="col-sm-6">
            <%
                default = 'cn'
                value = conf['ldap.server.schema.group.name']
                if not value: value = default
            %>
            <label for="ldap-server-schema-group-name" class="control-label">LDAP Group name attribute</label>
            <input class="form-control" id="ldap-server-schema-group-name"
                   name="ldap_server_schema_group_name" type="text"
                   value="${value}">
            <div class="help-block">Default is <code>${default}</code>.</div>
        </div>
    </div>

    <div class="row">
        <div class="col-sm-6">
            <label for="ldap-groupsyncing-schedule" class="control-label">Syncing schedule</label>
            <select class="form-control" id="ldap-groupsyncing-schedule" name="ldap_groupsyncing_schedule_enum">
                <option value="ONE-HOUR">Every Hour</option>
                <option value="THREE-HOURS">Every 3 Hours</option>
                <option value="DAILY">Daily</option>
            </select>
            <div class="help-block">Periodically sync with your LDAP Endpoint.</div>
            <br />
        </div>
        <div class ="col-sm-6" id="ldap-groupsync-time-selection">
            <%
            hours = range(1,13)
            hours.insert(0, hours.pop())
            times = [str(hour)+":00 " + ampm for ampm in ["AM", "PM"] for hour in hours]
            selected = conf['ldap.groupsyncing.time']
            if not selected: selected = "12:00 AM"
            %>
            <label for="ldap-groupsyncing-time" class="control-label">Time:</label>
            <select class="form-control" id="ldap-groupsyncing-time"
                    name="ldap_groupsyncing_time">
                % for time in times:
                    ${makeTimeOption(time, selected)}
                % endfor
            </select>
            <div class="help-block">Specific local time to sync with your LDAP Endpoint.
                    Default is <code>12:00 AM</code>.</div>
        </div>
        <%def name="makeTimeOption(time, selected)">
            <option value='${time}'
            %if selected==time:
                selected='selected'
            %endif
            >${time}</option>
        </%def>
    </div>

</%def>

<%modal:modal>
    <%def name="id()">success-modal</%def>
    <%def name="title()">You are almost done</%def>

    <p>The configuration is saved. However, to complete switching to the new identity system,
        please <a href="https://support.aerofs.com/hc/en-us/articles/204592834" target="_blank">read
        this article</a> and instruct users to update their passwords as necessary.</p>

    <%def name="footer()">
        <a href="#" data-dismiss="modal" class="btn btn-primary">Got It</a>
    </%def>
</%modal:modal>

<%progress_modal:progress_modal>
    <%def name="id()">identity-modal</%def>
    Please wait while we apply changes...
</%progress_modal:progress_modal>

<%block name="scripts">
    <%loader:scripts/>
    <%progress_modal:scripts/>
    ## spinner support is required by progress_modal
    <%spinner:scripts/>

    <script>
        var ldapOptionChanged = false;
        $(document).ready(function() {
            initializeProgressModal();

            %if local.local_auth:
                localSelected();
            %elif local.external_auth:
                ldapSelected();
            %elif local.saml_auth:
                samlSelected();
            %endif

            var ldapGroupSyncSchedule = "DAILY";

            %if local.ldap_group_sync_schedule_enum:
                ldapGroupSyncSchedule = "${local.ldap_group_sync_schedule_enum}";
            %endif

            $('#ldap-groupsyncing-schedule').val(ldapGroupSyncSchedule)

            if (ldapGroupSyncSchedule == "DAILY" ) {
                $('#ldap-groupsync-time-selection').show();
            } else {
                $('#ldap-groupsync-time-selection').hide();
            }

            ## Listen to any changes to LDAP options.
            ## LDAP options start with 'ldap_' per identity_view.py
            $('input[name^="ldap_"]').change(function () {
                ldapOptionChanged = true;
            })

            var utcoffset = document.createElement("input");
            utcoffset.setAttribute("type", "hidden");
            utcoffset.setAttribute("name", "utc_offset");
            utcoffset.setAttribute("value", getTimezoneOffset());
            document.getElementById("identity-form").appendChild(utcoffset);
        });

        $('#identity-modal').modal({
            backdrop: 'static',
            keyboard: false,
            show: false
        });

        $('#ldap-groupsyncing-schedule').change(function() {
            if ($(this).val() == 'DAILY') {
                $('#ldap-groupsync-time-selection').show();
            } else {
                 $('#ldap-groupsync-time-selection').hide();
            }
        });

        function localSelected() {
            ## Remove 'required' attribute otherwise Chrome would complain when
            ## submitting the form since required fields are invisible. See:
            ## http://stackoverflow.com/questions/7168645/invalid-form-control-only-in-google-chrome
            ## attribute.
            $('.form-control.ldap-required').removeAttr('required');
            $('.form-control.saml-required').removeAttr('required');
            $('#ldap-options').hide();
            $('#saml-options').hide();
        }

        function getTimezoneOffset() {
            var date = new Date();
            return (date.getTimezoneOffset() / 60).toString();
        }

        function ldapSelected() {
            $('.form-control.ldap-required').attr('required', 'required');
            $('.form-control.saml-required').removeAttr('required');
            $('#saml-options').hide();
            $('#ldap-options').show();
        }

        function samlSelected() {
            $('.form-control.saml-required').attr('required', 'required');
            $('.form-control.ldap-required').removeAttr('required');
            $('#ldap-options').hide();
            $('#saml-options').show();
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

        function ldapGroupsToggled(checkbox) {
            if (checkbox.checked) {
                $('#ldap-group-options').show();
            } else {
                $('#ldap-group-options').hide();
            }
        }

        function submitForm() {
            var $progressModal = $('#identity-modal');
            $progressModal.modal('show');
            var always = function() {
                $progressModal.modal('hide');
            };

            var authenticator = $('input[name="authenticator"]:checked').val();
            if (authenticator == 'local_credential' || authenticator == 'SAML') {
                post(always);
            } else if(authenticator == 'external_credential') {
                validateAndSubmitLDAPForm(always);
            }
        }

        function submitSignupRestrictionForm() {
            var $progressModal = $('#identity-modal');
            $progressModal.modal('show');
            var always = function() {
                $progressModal.modal('hide');
            };

            console.log($('#signup-restriction-form').serialize());

            $.post('${request.route_path('json_set_signup_restriction')}',
                    $('#signup-restriction-form').serialize())
            .done(function() {
                reboot('current', function() {
                    showSuccessMessage('Updated New User Invitation Restrictions.');
                    always();
                },
                ## fail
                function(xhr) {
                    showErrorMessageFromResponse(xhr);
                    always();
                });
            })
            .error(function (xhr) {
                showErrorMessageFromResponse(xhr);
                always();
            });
        }

        function validateAndSubmitLDAPForm(always) {
            if (!validateLDAPForm(always)) return;

            ## Verify LDAP if any LDAP options have changed or the user switches
            ## from local auth to LDAP.
            var wasLocalAuth = ${str(local.local_auth).lower()};
            if (ldapOptionChanged || wasLocalAuth) {
                console.log("ldap opt changed. test new opts");

                $.post('${request.route_path('json_verify_ldap')}',
                        $('#identity-form').serialize())
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
            var $idp_cert = $('#saml-idp-x509_certificate');
            $cert.val($.trim($cert.val()));
            $idp_cert.val($.trim($idp_cert.val()));

            $.post('${request.route_path('json_set_identity_options')}',
                    $('#identity-form').serialize())
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
            $('.form-control[required]').each(function() {
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
            reboot('current', function() {
                var $successModal = $('#success-modal');
                $successModal.modal('show');
                $successModal.on('hidden.bs.modal', function() {
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
            function(xhr) {
                showErrorMessageFromResponse(xhr);
                always();
            });
        }
    </script>
</%block>
