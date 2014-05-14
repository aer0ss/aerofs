<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Monitoring" %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="bootstrap" file="bootstrap.mako"/>
<%namespace name="modal" file="modal.mako"/>
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>

<h2>Service status monitor</h2>

<p>Your AeroFS appliance exposes the internal service health monitor as a web service.
The monitoring endpoint requires HTTP authentication using the special-purpose access token
given below.</p>

<hr>
%if pw_exists:
    ${replace_existing_pw()}
%else:
    ${create_new_pw()}
%endif

<%def name="create_new_pw()">
    <div class="row">
        <div class="col-sm-12">
            <a class="btn btn-primary" onclick="autoGenerate(); return false;">
                Create Access Token
            </a>
        </div>
    </div>
</%def>

<%def name="replace_existing_pw()">
    <div class="row">
        <div class="col-sm-6">
            <dl class="dl-horizontal" style="margin-top: auto">
                <dt>Monitoring URL:</dt>
                <dd><code>${base_url}/monitor</code></dd>
                <dt>Username:</dt>
                <dd>${username}</dd>
                <dt>Password:</dt>
                <dd>${password}</dd>
                <br>
                <dt></dt>

                <dd>
                    <a class="btn btn-default" onclick="confirmGenerate(); return false;">
                        Generate new access token
                    </a>
                </dd>
            </dl>
        </div>
    </div>

    <div class="row">
        <div class="col-sm-12">
        <p>Automated systems can provide credentials in an HTTP Authorization header:</p>

        <pre>curl -H 'Authorization: Basic ${base64_str}' ${base_url}/monitor</pre>
        </p>
    </div>
</%def>

<%modal:modal>
    <%def name="id()">confirm-modal</%def>
    <%def name="title()">Regenerate now?</%def>

    <p>This will invalidate your existing monitoring credentials.
    You will need to update any systems using the existing values. Are you sure?</p>

    <%def name="footer()">
        <a href="#" class="btn btn-default" data-dismiss="modal">Keep Existing</a>
        <a href="#" id="confirm-btn" class="btn btn-danger" data-dismiss="modal">Regenerate</a>
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
        $(document).ready(function() {
            initializeProgressModal();
        });

        ## Pop up a confirm modal, when click through then generate a credential
        function confirmGenerate() {
            var $modal = $('#confirm-modal');
            $('#confirm-btn').off().on('click', function() {
                $modal.modal('hide');
                post();
            });
            $modal.modal('show');
        }

        ## Generate a credential without the confirmation popup
        function autoGenerate() {
            post();
        }

        function post() {
            var $progressModal = $('#${progress_modal.id()}');
            $progressModal.modal('show');
            var always = function() {
                $progressModal.modal('hide');
            };

            $.post('${request.route_path('json_regenerate_monitoring_cred')}',
                    $('form').serialize())
            .done(function() {
                updateMonitoringPassword(always);
            })
            .error(function (xhr) {
                showErrorMessageFromResponse(xhr);
                always();
            });
        }

        function updateMonitoringPassword(always) {
            runBootstrapTask('update-monitoring-password',
                ## done
                function() {
                    ## redirect back to the monitoring page so the updated creds show
                    window.location.assign('${request.route_path('monitoring')}');
                    always();
                },
                ## fail
                always);
        }
    </script>
</%block>
