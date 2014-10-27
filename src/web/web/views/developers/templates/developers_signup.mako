<%inherit file="marketing_layout.mako"/>
<%! page_title = "Developers | Sign Up" %>

<%def name="home_url()">
    ${request.route_path('marketing_home')}
</%def>

<%def name="_text_input(name, label, type='text', required='false')">
    <div class="form-group">
        <label class="control-label col-sm-4" for="${name}">${label}</label>
        <div class="col-sm-8">
            <input type="${type}" class="form-control" id="${name}" name="${name}"
            ${'required' if required == 'true' else ''}
            >
        </div>
    </div>
</%def>

<%def name="_text_area(name, label, required='false')">
    <div class="form-group">
        <label class="control-label col-sm-4" for="${name}">${label}</label>
        <div class="col-sm-8">
            <textarea class="form-control" id="${name}" name="${name}"
             ${'required' if required == 'true' else ''}></textarea>
        </div>
    </div>
</%def>

<div class="row" id="form-row">
    <div class="col-sm-12">
        <h1 class="text-center">Sign Up For a Developer License</h1>
    </div>
    <div class="col-md-6 col-md-offset-3">
        <form id="dev-form" class="form-horizontal">
            ${_text_input("first_name", "First name:", 'text', 'true')}
            ${_text_input("last_name", "Last name:", 'text', 'true')}
            ${_text_input("email", "Email:", 'email', 'true')}
            ${_text_area("description", "Why do you want a developer license?", 'true')}
            <div class="form-group">
                <div class="col-sm-8 col-sm-offset-4">
                    <p class="help-block">By submitting this form you are agreeing to the <a href="/developers/terms/">AeroFS Developer License Agreement</a>.</p>
                </div>
            </div>
            <div class="form-group">
                <div class="col-sm-8 col-sm-offset-4">
                    <input id="dev-form-submit" type="submit" class="btn btn-primary" value="Sign Up">
                </div>
            </div>
        </form>
    </div>
</div>

<div id="success-row" class="row" style="display:none">
    <div class="col-sm-12">
        <h1 class="text-center">Signed Up!</h1>
    </div>
    <div class="col-md-6 col-md-offset-3">
        <p>You've successfully requested a Developer License for AeroFS Private Cloud. Our team will generate a developer license key for you within one business day.</p>
        <p>In the meantime, please email us at <a href="mailto:api@aerofs.com">api@aerofs.com</a> if you have any questions.</p>
        <a href="/" class="btn btn-primary">Return Home</a>
    </div>
</div>

<%block name="scripts">
    <script src="${request.static_path('web:static/js/jquery.validate.min.js')}"></script>
    <script>
        $(document).ready(function() {
            $('#first-name').focus();
            $('#dev-form').submit(function() {
                if (!$("#dev-form").valid()) return false;
                setEnabled($("#dev-form-submit"), false);
                var serializedData = $(this).serialize();
                $.post("${request.route_path('json.developers_signup')}", serializedData)
                .always(function() { setEnabled($("#dev-form-submit"), true); })
                .done(function(response, textStatus, jqXHR) {
                    if (analytics) {
                        analytics.identify(response['email']);
                    }
                    $("#form-row").hide();
                    $("#success-row").show();
                })
                .fail(showErrorMessageFromResponse);

                return false;
            });
        });
    </script>
</%block>
