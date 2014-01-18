<%inherit file="marketing_layout.mako"/>
<%! page_title = "Appliance Maintenance" %>

<%namespace name="license_common" file="license_common.mako"/>
<%namespace name="version" file="version.mako"/>
<%namespace name="no_ie" file="no_ie.mako"/>
<%namespace name="csrf" file="csrf.mako"/>

<%block name="top_navigation_bar_mobile">
    <%version:version_top_nav_item_mobile/>
</%block>

<%block name="top_navigation_bar_desktop">
    <%version:version_top_nav_item_desktop/>
</%block>

<div class="span8 offset2">
    <div class="text-center">
        <h3>Sign in to Manage Appliance</h3>

        <p>Please sign in with your license to manage this appliance.</p>

        ## The enctype is needed for file uploads
        <form method="post" enctype='multipart/form-data'
              action="${request.route_path('maintenance_login_submit')}">
            ${csrf.token_input()}
            <input type="hidden" name="${url_param_next}" value="${next}">

            ${upload_license_button.button('license-file', url_param_license)}

            <p>
                <button class="btn" id="continue-btn" type="submit" style="width: 180px">
                    Continue</button>
            </p>
        </form>
    </div>
</div>

<%block name="scripts">
    <%no_ie:scripts/>

    ${license_common.scripts('license-file', 'continue-btn')}

    <script>
        $(document).ready(function() {
            $('form').submit(function() {
                setEnabled($('#continue-btn'), false);
                return true;
            });
        });
    </script>
</%block>
