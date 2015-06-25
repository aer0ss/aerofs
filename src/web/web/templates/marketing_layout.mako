## This template is used for non-dashboard pages for private deployment.

<%inherit file="base_layout.mako"/>
<%namespace name="navigation" file="navigation.mako"/>

<%def name="home_url()">
    ${request.route_path('dashboard_home')}
</%def>

<%block name="top_navigation_bar_mobile">
</%block>

<%block name="top_navigation_bar_tablet">
</%block>

<%block name="top_navigation_bar_desktop">
    <%navigation:marketing_links/>
</%block>

<%block name="footer">
    <footer>
        <div class="container">
            <div class="row">
                <div class="col-sm-12" id="footer-span">
                    <ul class="list-inline">
                        <li class="pull-right">&copy; Air Computing Inc. 2015</li>
                    </ul>
                </div>
            </div>
        </div>
    </footer>
</%block>

## Main body
${next.body()}
