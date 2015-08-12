<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Customization" %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="loader" file="loader.mako"/>
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>

<h2>Customization</h2>

<p class="page-block">As an admin, you can customize your Private Cloud Web Portal to make it unique to your organization.</p>

<div class="page-block">
    ${customize_banner_form()}
</div>

<%def name="customize_banner_form()">
    <form method="POST" class="form-horizontal" onsubmit="submitForm(); return false;">
##     <form method="POST" class="form-horizontal" role="form">
        ${csrf.token_input()}

        <h4>Banner Customization</h4>
        <p>Banner customization allows admins to display custom messages that will be visible to all organization
            members from the AeroFS Web Interface. Simply enter your message in the textbox below to specify the content
            of your banner. The textbox also accepts raw HTML.</p>



        <div class="form-group">
            <div class="col-sm-12">
                <label for="customization_banner_text">Banner Text:</label>
                <textarea id="customization_banner_text" name="customization_banner_text" class="form-control"
                          placeholder="Enter Banner Text">${maintenance_custom_banner_text}</textarea>
            </div>
        </div>
        <div class="form-group">
            <div class="col-sm-6">
                <button id="save-btn" class="btn btn-primary">Save</button>
            </div>
        </div>
    </form>
</%def>

<%progress_modal:html>
    Configuring your customization...
</%progress_modal:html>

<%block name="scripts">
    <%loader:scripts/>
    <%spinner:scripts/>
    <%progress_modal:scripts/>
    <script>
        $(document).ready(function() {
            initializeProgressModal();
        });

        function submitForm() {
            var $progress = $('#${progress_modal.id()}');
            $progress.modal('show');

            $.post("${request.route_path('customization')}",
                    $('form').serialize())
            .done(restartServices)
            .fail(function(xhr) {
                showErrorMessageFromResponse(xhr);
                $progress.modal('hide');
            });
        }

        function restartServices() {
            var $progress = $('#${progress_modal.id()}');
            reboot('current', function() {
                $progress.modal('hide');
                showSuccessMessage('New configuration is saved.');
            }, function(xhr) {
                $progress.modal('hide');
                showErrorMessageFromResponse(xhr);
            });
        }
    </script>
</%block>



