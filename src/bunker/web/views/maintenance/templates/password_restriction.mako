<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Password Restriction" %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="loader" file="loader.mako"/>
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>

<head>
    <link href="${request.static_path('web:static/css/bootstrap-slider.css')}" rel='stylesheet'/>
</head>

<h2>Password Restriction</h2>

<div class="page-block">
    ${password_restriction_options_form()}
</div>

<%def name="password_restriction_options_form()">
    <form id="password-restriction-form" method="POST" onsubmit="submitPasswordRestrictionForm(); return false;">
        ${csrf.token_input()}

<h4>Password Requirements</h4>

 <p>Admins can set requirements for users' passwords such as minimum length of passwords and enforce
    passwords to contain alphanumeric characters.
 </p>
        <div class="row">
            <div class="col-sm-6">
                <label>Require at least one number and one letter?</label>
            </div>
        </div>

        <div class="radio">
            <label>
                <input type='radio' name='numbers-letters-required' value="true"
                    %if is_numbers_letters_required:
                        checked
                        %endif
                        >
                        Yes
            </label>
        </div>

        <div class="radio">
            <label>
                <input type='radio' name='numbers-letters-required' value="false"
                    %if not is_numbers_letters_required:
                        checked
                        %endif
                        >
                        No
            </label>
        </div>
        <br/>

        <div class="row">
            <div class="col-sm-4">
                <label>Minimum password length:</label>
            </div>
        </div>
        <br/>

        <div class="row">
            <div class="col-sm-4">
                <div>
                    <input id="passwordLengthSlider" type="text" name="min-password-length" data-slider-min="6"
                           data-slider-max="12" data-slider-step="1" data-slider-value="${min_password_length}"/&t
                        <span>Password Length: <span id="passwordLengthSliderVal">${min_password_length}
                        </span></span>
                </div>
            </div>
        </div>
        <br/>

<h4>Password Expiration</h4>

 <p>Admins can set an expiration period, in number of months, for users' passwords. When a user's password expires,
    they will be prompted to reset their password. Users will not be able to access AeroFS services until they
    successfully complete the password reset.
 </p>

        <br/>
        <div class="row">
            <div class="col-sm-4">
                <div>
                    <input id="expirationPeriodSlider" type="text" name="password-expiration-period-months" data-slider-min="0"
                           data-slider-max="12" data-slider-step="1" data-slider-value="${expiration_period_months}"/&t
                        <span>Expiration Period: <span id="expirationPeriodSliderVal">${expiration_period_months_str}</span>
                        </span>

                </div>
            </div>
        </div>
        <br/>


        <div class="row">
            <div class="col-sm-4">
                <button type="submit" id="save-btn" class="btn btn-primary">Save</button>
            </div>
        </div>

    </form>
</%def>

<br/>

<%progress_modal:html>
    Applying changes...
</%progress_modal:html>

<%block name="scripts">
    <%loader:scripts/>
    <%spinner:scripts/>
    <%progress_modal:scripts/>

<script type='text/javascript' src="${request.static_path('web:static/js/bootstrap-slider.js')}"></script>

    <script>

        $("#expirationPeriodSlider").slider();
        $("#expirationPeriodSlider").on("change", function(ev) {
            if (ev.value.newValue == 0) {
                 $("#expirationPeriodSliderVal").text("Never");
            } else if (ev.value.newValue == 1) {
                $("#expirationPeriodSliderVal").text(ev.value.newValue + " Month");
            } else {
                $("#expirationPeriodSliderVal").text(ev.value.newValue + " Months");
            }
        });

        $("#passwordLengthSlider").slider();
        $("#passwordLengthSlider").on("change", function(ev) {
            $("#passwordLengthSliderVal").text(ev.value.newValue);
        });

        function submitPasswordRestrictionForm() {
            var $progress = $('#${progress_modal.id()}');
            $progress.modal('show');

            $.post("${request.route_path('json_set_password_restriction')}",
                    $('#password-restriction-form').serialize())
            .done(restartPasswordServices)
            .fail(function(xhr) {
                showErrorMessageFromResponse(xhr);
                $progress.modal('hide');
            });
        }

        function restartPasswordServices() {
            var $progress = $('#${progress_modal.id()}');
            reboot('current', function() {
                $progress.modal('hide');
                showSuccessMessage('New configuration is saved.');
            }, function(xhr) {
                $progress.modal('hide');
                showErrorMessageFromResponse(xhr);
            });
        }

        function expiryEnabled() {
            $('#expiration-options').show();
        }

        function expiryDisabled() {
            $('#expiration-options').hide();
        }

    </script>
</%block>