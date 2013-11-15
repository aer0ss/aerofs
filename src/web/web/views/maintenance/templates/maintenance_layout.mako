<%inherit file="marketing_layout.mako"/>
<%namespace name="navigation" file="navigation.mako"/>
<%namespace name="no_ie" file="no_ie.mako"/>

<%no_ie:scripts/>

## N.B. dashboard_layout.mako uses the same layout
<div class="row">
    ## Left navigation bar
    <div class="span2 offset1">
        <ul class="nav nav-list left-nav">
            <li class="nav-header">My Appliance</li>
            <%
                links = [
                    ('status', _("Service Status")),
                    ('upgrade_appliance', _("Upgrade")),
                    ('backup_appliance', _("Backup")),
                    ('setup', _("Setup")),
                ]
            %>
            % for link in links:
                ${navigation.link(link)}
            % endfor

            <li class="nav-header">My AeroFS</li>
            ${navigation.link(('dashboard_home', 'Home'))}
        </ul>
    </div>

    ## Main body
    <div class="span8">
        ${next.body()}
    </div>
</div>
