## This template is used for non-dashboard pages for public deployment.

<%inherit file="base_layout.mako"/>

<%namespace name="navigation" file="navigation.mako"/>

<style type="text/css">
    ## TODO (WW) move it to base_layout.mako?
    ## Show the navigation bar's dropdown menus on mouse over
    ul.nav li.dropdown:hover > ul.dropdown-menu {
        display: block;
    }
    .top-nav-dropdown {
        margin-top: -10px;
    }
    .top-contact {
        margin-left: -10px;
    }
</style>

<%block name="meta_tags">
    ## descriptions should be ~<=155 characters in length
    <meta name="description" content="Private cloud file sync and share. AeroFS enables secure, unlimited data transfer for large and sensitive files on Windows, Mac, Linux, and Android." />
</%block>

<%block name="top_navigation_bar_mobile">
    <li class="dropdown hidden-lg hidden-md pull-right"><span class="glyphicon glyphicon-th-list"></span> Menu
        <ul class="dropdown-menu">
            <li><a href="${request.route_path('dashboard_home')}">Sign in</a></li>
            <li class="divider"></li>
            <li><a href="mailto:sales@aerofs.com">
                sales@aerofs.com</a></li>
            <li><a style="font-weight: normal; color: #222">1-800-656-AERO</a></li>
        </ul>
    </li>
</%block>

<%block name="top_navigation_bar_desktop">
    <%
        if not request.matched_route:
            sign_in_button = True
        else:
            route_name = request.matched_route.name
            sign_in_button = route_name != 'login'
    %>

    %if sign_in_button:
        <li class="pull-right hidden-xs hidden-sm"><a href="${request.route_path('dashboard_home')}">Sign in</a></li>
    %endif
</%block>

<%def name="home_url()">
    ${request.route_path('marketing_home')}
</%def>

<%block name="page_view_tracker">
    <script>
        ## We only track marketing page views
        if (analytics) analytics.page("${self.attr.page_title}");
    </script>
</%block>

<%block name="footer">
    <footer>
        <div class="container">
            <div class="row">
                <div class="col-sm-12" id="footer-span">
                    <ul class="list-inline">
                        <li><a href="/about">About</a></li>
                        <li><a href="/pricing">Pricing</a></li>
                        <li><a href="/press">Press</a></li>
                        <li><a href="/careers">Careers</a></li>
                        <li><a href="/terms/#tos">Terms</a></li>
                        <li><a href="https://support.aerofs.com">Support</a></li>
                        <li><a href="https://blog.aerofs.com">Blog</a></li>
                        <li><a href="https://www.twitter.com/aerofs">Twitter</a></li>

                        <li class="pull-right">&copy; Air Computing Inc. 2014</li>
                    </ul>
                </div>
            </div>
        </div>
    </footer>
</%block>

<%block name="layout_scripts">
    <script>
        $(document).ready(function() {
            ## Bootstrap doesn't follow the link of an menu item if the item is
            ## attached with a dropdown submenu. Follow the link manually here.
            $(".link-with-dropdown").click(function(e) {
                ## Follow the link of the menu item that have
                document.location = $(this).attr('href');
                return false;
            });
            $('#contact-phone').tooltip({
                placement: 'bottom',
                title: '1-800-656-AERO'
            });
            $('#contact-twitter').tooltip({
                placement: 'bottom',
                title: '@aerofs'
            });
            $('#contact-email').tooltip({
                placement: 'bottom',
                title: 'business@aerofs.com'
            });
        });
    </script>
</%block>

## Main body
${next.body()}
