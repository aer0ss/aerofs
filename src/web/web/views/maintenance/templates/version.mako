<%! from web.version import get_current_version %>

<%def name="version_top_nav_item_mobile()">
    <li>
        <a href="https://support.aerofs.com/entries/23864878" target="_blank">
            Version ${get_current_version()}</a>
    </li>
</%def>

<%def name="version_top_nav_item_desktop()">
    <li class="pull-right" style="font-weight: normal">
        <a href="https://support.aerofs.com/entries/23864878" target="_blank">
            v${get_current_version()}</a>
    </li>
</%def>