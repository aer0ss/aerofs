## Name this file marketing_layout.mako so some templates that are shared between
## web and bunker can work for both projects.

<%inherit file="base_layout.mako"/>
<%namespace name="no_ie" file="no_ie.mako"/>

<%no_ie:scripts/>

<%! from web.version import get_private_version %>

<%def name="home_url()">
    ${request.route_path('maintenance_home')}
</%def>

<%block name="top_navigation_bar_mobile">
    <li class="visible-xs">
        <a href="https://support.aerofs.com/entries/23864878" target="_blank">
            Version ${get_private_version(request.registry.settings)}</a>
    </li>
</%block>

<%block name="top_navigation_bar_tablet">
    <li class="hidden-lg hidden-xs">
        <a href="https://support.aerofs.com/entries/23864878" target="_blank">
            Version ${get_private_version(request.registry.settings)}</a>
    </li>
</%block>

<%block name="top_navigation_bar_desktop">
    <li class="pull-right visible-lg" style="font-weight: normal;">
        <a href="https://support.aerofs.com/entries/23864878" target="_blank">
            v${get_private_version(request.registry.settings)}</a>
    </li>
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

<div class="row">
    ## Main body
    ${next.body()}
</div>

<%block name="css">
    <link href="${request.static_path('web:static/css/compiled/aerofs-bunker.min.css')}" rel="stylesheet">
</%block>
