<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Customization" %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="loader" file="loader.mako"/>
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>

<h2>Customization</h2>

<p class="page-block">As an admin, you can customize your Private Cloud Web Portal to make it unique
to your organization.</p>

<div class="page-block">
    ${customize_banner_form()}
</div>

<%def name="customize_banner_form()">
    <form method="POST" class="form-horizontal" enctype="multipart/form-data" onsubmit="submitForm(); return false;">
        ${csrf.token_input()}

        <h4>Banner Customization</h4>
        <p>Banner customization allows admins to display custom messages that will be visible to all
            organization members from the AeroFS Web Interface. Simply enter your message in the
            textbox below to specify the content of your banner.</p>
        <p>The textbox also accepts HTML &lta&gt tags for linking to other pages. All
            other HTML tags will be ignored (i.e. escaped).</p>

        <div class="form-group">
            <div class="col-sm-12">
                <label for="customization_banner_text">Banner Text:</label>
                <textarea id="customization_banner_text" name="customization_banner_text" class="form-control"
                          placeholder="Enter Banner Text">${maintenance_custom_banner_text}</textarea>
            </div>
        </div>

        <hr/>

        <h4>Users and Groups Web Pages</h4>
        <p>
            Check the boxes below to enable access to the Users and Groups web pages for users
            without admin privileges.
        </p>
        <label class="checkbox">
            <input type='checkbox' name='customization_enable_user_view'
                   value='customization_enable_user_view'
                   %if customization_enable_user_view:
                       checked
                   %endif
            >
            Show Users
        </label>
        <label class="checkbox">
            <input type='checkbox' name='customization_enable_group_view'
                   value='customization_enable_group_view'
                   %if customization_enable_group_view:
                       checked
                   %endif
            >
            Show Groups
        </label>

        <hr/>

        <h4>White label</h4>

        <p>You may optionally upload a custom 144x44 logo for AeroFS. Changes will be reflected on
            the main AeroFS web portal. </p>
        <br/>

        <div class="col-sm-3" id="white-label-container">
            <p>Current logo:</p>
            %if request.registry.settings.get('customization.logo'):
                <img src="/static/img/logo_custom.png" width="144" height="40" alt="AeroFS"/>
            %else:
                <img src="/static/img/aero_logo.png" width="151" height="37" alt="AeroFS"/>
            <br/><br/>
            %endif
        </div>

        <div class="form-group">
            <div class="col-sm-12">
                <label class="radio">
                    <input type="radio" name="enable-white-label-logo" value="false" id="aerofs-logo"
                     %if not request.registry.settings.get('customization.logo'):
                        checked
                     %endif
                    />AeroFS logo
                </label>
                <label class="radio">
                    <input type="radio" name="enable-white-label-logo" value="true" id="custom-logo"
                    %if request.registry.settings.get('customization.logo'):
                        checked
                     %endif
                     />Custom Logo:
                        <input type="file" name="white-label-logo-selector" id="white-label-logo-selector">
                    <input type="hidden" name="white-label-logo" id ="white-label-logo">
                    <p class="help-block">Please upload a 151x37 proportionally sized image.</p>
                </label>
            </div>
        </div>

        <div class="form-group">
            <div class="col-sm-6">
                <button id="save-btn" class="btn btn-primary">Save</button>
            </div>
        </div>
    </form>
</%def>

<%progress_modal:progress_modal>
    <%def name="id()">customize-modal</%def>
    Configuring your customization...
</%progress_modal:progress_modal>

<%block name="scripts">
    <%loader:scripts/>
    <%spinner:scripts/>
    <%progress_modal:scripts/>
    <script>
        $(document).ready(function() {
            initializeProgressModal();
            linkFileSelectorToField_base64('#white-label-logo-selector', '#white-label-logo');
        });

        $('#customize-modal').modal({
            backdrop: 'static',
            keyboard: false,
            show: false
        });

        $("#white-label-logo-selector").change(function(){
            document.getElementById("custom-logo").checked = true;
        });
        function submitForm() {
            var $progress = $('#customize-modal');
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
            var $progress = $('#customize-modal');
            reboot('current', function() {
                $progress.modal('hide');
                showSuccessMessage('New configuration is saved.');
                location.reload();
            }, function(xhr) {
                $progress.modal('hide');
                showErrorMessageFromResponse(xhr);
            });
        }
    </script>
</%block>



