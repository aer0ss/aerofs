## Name this file marketing_layout.mako so some templates that are shared between
## web and bunker can work for both projects.

<%inherit file="base_layout.mako"/>
<%namespace name="no_ie" file="no_ie.mako"/>

<%no_ie:scripts/>

<%! from web.version import get_current_version %>

<%def name="home_url()">
    ${request.route_path('maintenance_home')}
</%def>

<%block name="top_navigation_bar_mobile">
    <li>
        <a href="https://support.aerofs.com/entries/23864878" target="_blank">
            Version ${get_current_version()}</a>
    </li>
</%block>

<%block name="top_navigation_bar_desktop">
    <li class="pull-right" style="font-weight: normal">
        <a href="https://support.aerofs.com/entries/23864878" target="_blank">
            v${get_current_version()}</a>
    </li>
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

<div class="row">
    ## Main body
    ${next.body()}
</div>