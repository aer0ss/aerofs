<%inherit file="base_layout.mako"/>

<%!
    blog_url = "http://blog.aerofs.com"
%>

<%block name="top_navigation_bar">
    <%
        if not request.matched_route:
            sign_up_button = True
            sign_in_button = True
        else:
            route_name = request.matched_route.name
            sign_up_button = route_name != 'marketing_home'
            sign_in_button = route_name != 'login'
    %>

    <li><a href="${request.route_path('features')}">Features</a></li>
    <li><a href="${request.route_path('pricing')}">Pricing</a></li>
    <li><a href="${blog_url}">Blog</a></li>
    %if sign_up_button:
        <li class="pull-right">
            ## The 'highlight' string must be consistent with index.mako
            <a class="btn" id="nav-btn-sign-up" href="${request.route_path('marketing_home') + '?highlight=1'}">
                Sign up
            </a>
        </li>
    %endif
    %if sign_in_button:
        <li class="pull-right"><a href="${request.route_path('dashboard_home')}">Sign in</a></li>
    %endif
</%block>

<%block name="home_url">
    ${request.route_path('marketing_home')}
</%block>

<%block name="footer">
    <footer>
        <div class="container">
            <div class="row">
                <div class="span10 offset1" id="footer-span">
                    <ul class="inline">
                        <li><a href="${request.route_path('marketing_home')}">Home</a></li>
                        <li><a href="${request.route_path('download')}">Install</a></li>
                        <li><a href="http://support.aerofs.com">Support</a></li>
                        <li><a href="${blog_url}">Blog</a></li>
                        <li><a href="http://www.twitter.com/aerofs">Twitter</a></li>
                        <li><a href="${request.route_path('jobs')}">Jobs</a></li>
                        <li><a href="${request.route_path('about')}">About</a></li>
                        <li>|</li>
                        <li><a href="${request.route_path('terms')}">Privacy & Terms</a></li>
                        ## The "security" string link must be consistent with terms.mako
                        ## The security_link link is needed by terms.mako
                        <li><a id="security_link" href="${request.route_path('terms')}#security">Security</a></li>

                        <li class="pull-right">&copy; Air Computing Inc. 2013</li>
                    </ul>
                </div>
            </div>
        </div>
    </footer>
</%block>

## Main body
${next.body()}
