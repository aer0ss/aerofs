## This template is used for non-dashboard pages for private deployment.

<%inherit file="base_layout.mako"/>
<%namespace name="navigation" file="navigation.mako"/>

<%block name="home_url">
    ${request.route_path('dashboard_home')}
</%block>

<%block name="top_navigation_bar">
    <%navigation:marketing_links/>
</%block>

<%block name="footer">
    <footer>
        <div class="container">
            <div class="row">
                <div class="span10 offset1" id="footer-span">
                    <ul class="inline">
                        <li class="pull-right">&copy; Air Computing Inc. 2013</li>
                    </ul>
                </div>
            </div>
        </div>
    </footer>
</%block>

## Main body
${next.body()}