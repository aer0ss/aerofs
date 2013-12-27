<%namespace name="csrf" file="csrf.mako" import="token_input, token_param"
        inheritable="True"/>

<%! from web.util import is_private_deployment %>

##
## N.B. Match this file's content with Lizard's base.html
##
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />

    <title>
        ## See http://moz.com/learn/seo/title-tag for info on title tags
        ## Important details: 
        ## - titles should be <70 characters total
        ## - should not be too similar page to page (triggers "repeat content")
        ## - if the brand is young (e.g. not known), focus on keywords in the front


        ## The following code requires _every_ page to define a page title.
        ##
        ## Place the title definition to the top of the file:
        ## <%! page_title = "About Us" %>
        ## And remember to capitalize initials.
        %if len(self.attr.page_title) != 0:
            ${self.attr.page_title} |
        %endif
            AeroFS
    </title>

    ## canonical URL to be indexed by search engines. Prevents situations where search engines
    ## attempt to index http://www.aerofs.com, https://www.aerofs.com, and http(s)://aerofs.com
    ## resulting in "duplicate content" and devaluing each page...

    ## using www.aerofs.com instead of just aerofs.com because most people linking in to us from other sources use
    ## www.aerofs.com, so the domain authority of www.aerofs.com is already higher than aerofs.com

    ## need to check if we are in private cloud deployment mode. If not, disable ref canonical
    ## since we don't want the canonical pages to be www.aerofs.com
    %if not is_private_deployment(request.registry.settings):
        <%
            try:
                current_route_path = request.current_route_path()
            except Exception:
                current_route_path = None
        %>
        %if current_route_path:
            <link rel="canonical" href="https://www.aerofs.com${current_route_path}" />
        %endif
    %endif
    <%block name="meta_tags"/>

    ## fav and touch icons
    <link rel="shortcut icon" href="${request.static_path('web:static/img/favicon.ico')}">

    ## stylesheets
    ##
    ## N.B. to support private deployment, all static assets must be hosted
    ## locally as opposed to on 3rd-party servers.
    <link href="${request.static_path('web:static/css/google-open-sans.css')}" rel='stylesheet'>
    <link href="${request.static_path('web:static/css/bootstrap.css')}" rel="stylesheet">
    <link href="${request.static_path('web:static/css/responsive.css')}" rel="stylesheet">
    <link href="${request.static_path('web:static/css/aerofs.css')}" rel="stylesheet">

    <%block name="css"/>

    ## Le HTML5 shim, for IE6-8 support of HTML5 elements
    <!--[if lt IE 9]>
    <script src="${request.static_path('web:static/js/html5.js')}"></script>
    <![endif]-->

    %if not is_private_deployment(request.registry.settings):
        ${tracking_codes()}
    % else:
        ## Set the global analytics object to `false` so that we can do `if (analytics)` to check if mixpanel is enabled
        <script>window.analytics = false;</script>
    %endif

    <script><%block name="page_view_tracker"/></script>
</head>

<%def name="tracking_codes()">
    ## Segment.IO - Put in the header rather than footer as required by Segment.IO
    <script type="text/javascript">
        window.analytics||(window.analytics=[]),window.analytics.methods=["identify","track","trackLink","trackForm","trackClick","trackSubmit","page","pageview","ab","alias","ready","group","on","once","off"],window.analytics.factory=function(a){return function(){var t=Array.prototype.slice.call(arguments);return t.unshift(a),window.analytics.push(t),window.analytics}};for(var i=0;i<window.analytics.methods.length;i++){var method=window.analytics.methods[i];window.analytics[method]=window.analytics.factory(method)}window.analytics.load=function(a){var t=document.createElement("script");t.type="text/javascript",t.async=!0,t.src=("https:"===document.location.protocol?"https://":"http://")+"d2dq2ahtl5zl1z.cloudfront.net/analytics.js/v1/"+a+"/analytics.min.js";var n=document.getElementsByTagName("script")[0];n.parentNode.insertBefore(t,n)},window.analytics.SNIPPET_VERSION="2.0.6",
        window.analytics.load("${request.registry.settings['segmentio.api_key']}");
    </script>

    <script src="//cdn.optimizely.com/js/29642448.js"></script>
</%def>

<body>
    ## this wrapper is used to keep the footer at the bottom, even if the content
    ## height is less than the window height ("sticky" footer)
    <div id="wrapper">
        ## Horizontal navigation bar
        <div class="container" style="margin-bottom: 20px;">
            <div class="row">
                <div class="span10 offset1 visible-desktop" id="top-nav-span">
                    <ul class="nav nav-pills top-nav">
                        <li><a href="${self.home_url()}">
                            <img src="${request.static_path('web:static/img/logo_small.png')}" width="144" height="40" alt="AeroFS"/>
                        </a></li>
                        <%block name="top_navigation_bar_desktop"/>
                    </ul>
                </div>

                <div class="span10 offset1 hidden-desktop" style="margin-top: 14px">
                    <a href="${self.home_url()}"><img src="${request.static_path('web:static/img/logo_small.png')}" width="144" height="40" alt="AeroFS"/></a>
                    <div class="btn-group pull-right hidden-desktop">
                        <a href="#" class="btn dropdown-toggle"
                                data-toggle="dropdown">
                            <i class="icon-th-list"></i>
                        </a>
                        <ul class="dropdown-menu">
                            <%block name="top_navigation_bar_mobile"/>
                        </ul>
                    </div>
                </div>
            </div>
        </div>

        ## Message bar
        <div class="container">
            <div id="message_bar" class="span6 offset3 message_container"></div>
        </div>

        ## Main body
        <div class="container">
            ${next.body()}
        </div>

        ## this element has the same or larger height as the footer, and it
        ## ensures that the footer never overlaps the content
        <div id="footer-push"></div>
    </div>

    <%block name="footer"/>

    ## javascript
    ##==================================================
    ##Placed at the end of the document so the pages load faster

    ## N.B. to support private deployment, all static assets must be hosted
    ## locally as opposed to on 3rd-party servers.
    <script src="${request.static_path('web:static/js/jquery.min.js')}"></script>
    <script src="${request.static_path('web:static/js/jquery.easing.1.3.js')}"></script>
    <script src="${request.static_path('web:static/js/bootstrap.min.js')}"></script>
    <script src="${request.static_path('web:static/js/aerofs.js')}"></script>

    <%
        from web.util import get_last_flash_message_and_empty_queue
        ret = get_last_flash_message_and_empty_queue(request)
    %>

    %if ret:
        <script type="text/javascript">
            $(document).ready(function() {
                ## No need to encode message texts here. jQuery will do the job for
                ## us in show*Message() methods.
                %if ret[1]:
                    showSuccessMessage("${ret[0] | n}");
                %else:
                    showErrorMessage("${ret[0] | n}");
                %endif
            });
        </script>
    %endif

    ## Unlike the "scripts" block which is supposed to be overridden by leaf
    ## mako files in the inheritance tree, "layout_scripts" is meant to be
    ## overridden by *_layout.mako.
    <%block name="layout_scripts"/>

    <%block name="scripts"/>
</body>
</html>
