<%namespace name="csrf" file="csrf.mako" import="token_input"
        inheritable="True"/>
<%namespace name="segment_io" file="segment_io.mako"/>

<%! from web.util import is_private_deployment %>

##
## N.B. Match this file's content with Lizard's base.html
##
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <meta name="csrf-token" content="${request.session.get_csrf_token()}" />

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
    <link rel="apple-touch-icon" sizes="57x57" href="${request.static_path('web:static/favicons/apple-touch-icon-57x57.png')}">
    <link rel="apple-touch-icon" sizes="114x114" href="${request.static_path('web:static/favicons/apple-touch-icon-114x114.png')}">
    <link rel="apple-touch-icon" sizes="72x72" href="${request.static_path('web:static/favicons/apple-touch-icon-72x72.png')}">
    <link rel="apple-touch-icon" sizes="144x144" href="${request.static_path('web:static/favicons/apple-touch-icon-144x144.png')}">
    <link rel="apple-touch-icon" sizes="60x60" href="${request.static_path('web:static/favicons/apple-touch-icon-60x60.png')}">
    <link rel="apple-touch-icon" sizes="120x120" href="${request.static_path('web:static/favicons/apple-touch-icon-120x120.png')}">
    <link rel="apple-touch-icon" sizes="76x76" href="${request.static_path('web:static/favicons/apple-touch-icon-76x76.png')}">
    <link rel="apple-touch-icon" sizes="152x152" href="${request.static_path('web:static/favicons/apple-touch-icon-152x152.png')}">
    <link rel="apple-touch-icon" sizes="180x180" href="${request.static_path('web:static/favicons/apple-touch-icon-180x180.png')}">
    <link rel="icon" type="image/png" href="${request.static_path('web:static/favicons/favicon-192x192.png')}" sizes="192x192">
    <link rel="icon" type="image/png" href="${request.static_path('web:static/favicons/favicon-160x160.png')}" sizes="160x160">
    <link rel="icon" type="image/png" href="${request.static_path('web:static/favicons/favicon-96x96.png')}" sizes="96x96">
    <link rel="icon" type="image/png" href="${request.static_path('web:static/favicons/favicon-16x16.png')}" sizes="16x16">
    <link rel="icon" type="image/png" href="${request.static_path('web:static/favicons/favicon-32x32.png')}" sizes="32x32">
    <meta name="msapplication-config" content="${request.static_path('web:static/favicons/browserconfig.xml')}" />
    <meta name="msapplication-TileColor" content="#002060">
    <meta name="msapplication-TileImage" content="${request.static_path('web:static/favicons/mstile-144x144.png')}">
    <link rel="shortcut icon" href="${request.static_path('web:static/favicons/favicon.ico')}">

    ## stylesheets
    ##
    ## N.B. to support private deployment, all static assets must be hosted
    ## locally as opposed to on 3rd-party servers.
    <link href="${request.static_path('web:static/css/google-open-sans.css')}" rel='stylesheet'>
    <link href="${request.static_path('web:static/css/compiled/aerofs.min.css')}" rel="stylesheet">

    <%block name="css"/>

    ## Le HTML5 shim, for IE6-8 support of HTML5 elements
    <!--[if lt IE 9]>
        <script src="${request.static_path('web:static/js/html5.js')}"></script>
    <![endif]-->

    <%block name="tracking_codes">
        %if not is_private_deployment(request.registry.settings):
            ## because google analytics and pardot are critical to marketing, do not depend on
            ## segment.io. In general, we should use segment.io for non "critical" flows
            ## and when we're trying to test out new technologies

            ## this is google analytics code
            <script>
              (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
              (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
              m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
              })(window,document,'script','//www.google-analytics.com/analytics.js','ga');

              ga('create', 'UA-24554389-1', 'aerofs.com');
              ga('send', 'pageview');

            </script>

            ## this is pardot code
            <script>
                piAId = '33882';
                piCId = '1470';

                (function() {
                    function async_load(){
                        var s = document.createElement('script'); s.type = 'text/javascript';
                        s.src = ('https:' == document.location.protocol ? 'https://pi' : 'http://cdn') + '.pardot.com/pd.js';
                        var c = document.getElementsByTagName('script')[0]; c.parentNode.insertBefore(s, c);
                    }
                    if(window.attachEvent) { window.attachEvent('onload', async_load); }
                    else { window.addEventListener('load', async_load, false); }
                })();
            </script>
            
            <script src="//cdn.optimizely.com/js/29642448.js"></script>
            ${segment_io.code(request.registry.settings['segmentio.api_key'])}
        % else:
            ## Set the global analytics object to `false` so that we can do `if (analytics)` to check if mixpanel is enabled
            <script>window.analytics = false;</script>
        %endif
    </%block>

    <%block name="page_view_tracker"/>

    <%block name="head_scripts"/>
</head>

<body>
    ## this wrapper is used to keep the footer at the bottom, even if the content
    ## height is less than the window height ("sticky" footer)
    <div id="wrapper">
        ## Horizontal navigation bar
        <div class="container top-nav-wrapper">
            <div class="row">
                <div class="col-sm-12" id="top-nav-span">
                    <ul class="nav nav-pills top-nav">
                        <li class="logo"><a href="${self.home_url()}">
                            <img src="${request.static_path('web:static/img/logo_small.png')}" width="144" height="40" alt="AeroFS"/>
                        </a></li>
                        <%block name="top_navigation_bar_desktop"/>
                        <%block name="top_navigation_bar_tablet"/>
                        <%block name="top_navigation_bar_mobile"/>
                    </ul>
                </div>
            </div>
        </div>

        %if splash:
            <div class="container">
                <div id="splash" class="row">
                    <div class="col-sm-12">
                        <img id="splash-img" src="${request.static_path('web:static/img/splash.png')}" class="img-responsive">
                    </div>
                </div>
            </div>
        %endif

        ## Message bar
        <div class="container">
            <div class="row">
            <div id="error-wrap" class="col-sm-12">
                <div id="flash-msg-success" class="alert alert-success alert-dismissable" style="display: none">
                    <button type="button" class="close" data-dismiss="alert" aria-hidden="true">&times;</button>
                    <span id="flash-msg-success-body"></span>
                </div>
                <div id="flash-msg-error" class="alert alert-danger alert-dismissable" style="display: none">
                    <button type="button" class="close" data-dismiss="alert" aria-hidden="true">&times;</button>
                    <span id="flash-msg-error-body"></span>
                </div>
            </div>
            </div>
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
    <script src="${request.static_path('web:static/js/compiled/aerofs.js')}"></script>
    <script src="${request.static_path('web:static/js/compiled/csrf.js')}"></script>

    <%
        from web.util import get_last_flash_message_and_empty_queue
        ret = get_last_flash_message_and_empty_queue(request)
    %>

    %if ret:
        <script>
            $(document).ready(function() {
                ## No need to encode message texts here. jQuery will do the job for
                ## us in show*Message() methods.
                %if ret[1]:
                    showSuccessMessageUnsafe("${ret[0] | n}");
                %else:
                    showErrorMessageUnsafe("${ret[0] | n}");
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
