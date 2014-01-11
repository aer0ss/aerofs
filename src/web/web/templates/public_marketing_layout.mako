## This template is used for non-dashboard pages for public deployment.

<%inherit file="base_layout.mako"/>

<%namespace name="navigation" file="navigation.mako"/>
<%namespace name="sign_up_forms" file="sign_up_forms.mako"/>

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
        <li><a href="${request.route_path('product_overview')}">Product</a></li>
        <li><a href="${request.route_path('solutions_overview')}">Solutions</a></li>
        <li><a href="${request.route_path('security_overview')}">Security</a></li>
        <li><a href="${request.route_path('pricing')}">Pricing</a></li>
        <li class="divider"></li>
        <li><a href="${request.route_path('dashboard_home')}">Sign in</a></li>
        <li><a href="${request.route_path('pricing')}">Sign up</a></li>
        <li class="divider"></li>
        <li><a href="mailto:business@aerofs.com">
            business@aerofs.com</a></li>
        <li><a style="font-weight: normal; color: #222">1-800-656-AERO</a></li>
</%block>

<%block name="top_navigation_bar_desktop">
    <%
        if not request.matched_route:
            sign_up_button = True
            sign_in_button = True
        else:
            route_name = request.matched_route.name
            sign_up_button = route_name != 'marketing_home'
            sign_in_button = route_name != 'login'
    %>
    <li class="dropdown">
        <a class="dropdown-toggle link-with-dropdown" data-toggle="dropdown"
                href="${request.route_path('product_overview')}">
            <p>Product</p>
        </a>
        <ul class="dropdown-menu top-nav-dropdown">
            <%navigation:product_items/>
        </ul>
    </li>

    <li class="dropdown">
        <a class="dropdown-toggle link-with-dropdown" data-toggle="dropdown"
                href="${request.route_path('solutions_overview')}">
            <p>Solutions</p>
        </a>
        <ul class="dropdown-menu top-nav-dropdown">
            <%navigation:solutions_items/>
        </ul>
    </li>

    <li class="dropdown">
        <a class="dropdown-toggle link-with-dropdown" data-toggle="dropdown"
                href="${request.route_path('security_overview')}">
            <p>Security</p>
        </a>
        <ul class="dropdown-menu top-nav-dropdown">
            <%navigation:security_items/>
        </ul>
    </li>

    <li><a href="${request.route_path('pricing')}">Pricing</a></li>
    %if sign_up_button:
        <li class="pull-right">
            <a class="btn" id="nav-btn-sign-up" href="${request.route_path('pricing')}">
                Sign up
            </a>
        </li>
    %endif
    %if sign_in_button:
        <li class="pull-right"><a href="${request.route_path('dashboard_home')}">Sign in</a></li>
    %endif
    <li class="pull-right top-contact"><a href="http://www.twitter.com/aerofs">
        <i class="aerofs-icon-twitter" id="contact-twitter"></i></a></li>
    <li class="pull-right top-contact"><a href="mailto:business@aerofs.com">
        <i class="icon-envelope" id="contact-email"></i></a></li>
    <li class="pull-right top-contact"><a href="#" onclick="showEnterpriseContactForm(); return false;">
        <i class="aerofs-icon-earphone" id="contact-phone"></i></a></li>
</%block>

<%def name="home_url()">
    ${request.route_path('marketing_home')}
</%def>

<%block name="page_view_tracker">
    ## We only track marketing page views
    if (analytics) analytics.page("${self.attr.page_title}");
</%block>

<%block name="footer">
    <footer>
        <div class="container">
            <div class="row">
                <div class="span10 offset1" id="footer-span">
                    <ul class="inline">
                        <li><a href="${request.route_path('about')}">About</a></li>
                        <li><a href="${request.route_path('press')}">Press</a></li>
                        <li><a href="http://blog.aerofs.com">Blog</a></li>
                        <li><a href="http://www.twitter.com/aerofs">Twitter</a></li>
                        <li><a href="${request.route_path('careers')}">Careers</a></li>
                        <li><a href="${request.route_path('terms')}">Terms</a></li>
                        <li><a href="http://support.aerofs.com">Support</a></li>
                        <li><a href="${request.route_path('resources')}">Resources</a></li>
                        ## <li><a href="${request.route_path('developers_overview')}">Developers</a></li>

                        <li class="pull-right">&copy; Air Computing Inc. 2013</li>
                    </ul>
                </div>
            </div>
        </div>
    </footer>
</%block>

<%block name="layout_scripts">
    <%sign_up_forms:scripts/>
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

<%sign_up_forms:modals/>
