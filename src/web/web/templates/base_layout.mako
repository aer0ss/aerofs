<%namespace name="csrf" file="csrf.mako" import="token_input"
        inheritable="True"/>
<%namespace name="segment_io" file="segment_io.mako"/>

##
## N.B. Match this file's content with Lizard's base.html
##
<!DOCTYPE html>
<!--[if lt IE 7 ]> <html class="ie6" lang="en"> <![endif]-->
<!--[if IE 7 ]>    <html class="ie7" lang="en"> <![endif]-->
<!--[if IE 8 ]>    <html class="ie8" lang="en"> <![endif]-->
<!--[if IE 9 ]>    <html class="ie9" lang="en"> <![endif]-->
<!--[if (gt IE 9)|!(IE)]><!--> <html class="" lang="en"> <!--<![endif]-->
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <meta name="csrf-token" content="${request.session.get_csrf_token()}" />
    <meta http-equiv="X-UA-Compatible" content="IE=edge">

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

    ## Shims for IE6-8 support of HTML5 elements and @media selectors
    <!--[if lt IE 9]>
      <script src="${request.static_path('web:static/js/html5shiv.min.js')}"></script>
      <script src="${request.static_path('web:static/js/respond.min.js')}"></script>
      <script src="${request.static_path('web:static/js/json3.min.js')}"></script>
      <script src="${request.static_path('web:static/js/compiled/polyfills.js')}"></script>
    <![endif]-->

    ## N.B. to support private deployment, all static assets must be hosted
    ## locally as opposed to on 3rd-party servers.
    <script src="${request.static_path('web:static/js/jquery.min.js')}"></script>
    <script src="${request.static_path('web:static/js/jquery.easing.1.3.js')}"></script>
    <script src="${request.static_path('web:static/js/bootstrap.min.js')}"></script>
    <script src="${request.static_path('web:static/js/compiled/aerofs.js')}"></script>
    <script src="${request.static_path('web:static/js/compiled/csrf.js')}"></script>

    <%block name="tracking_codes">
        ## Set the global analytics object to `false` so that we can do `if (analytics)` to check if mixpanel is enabled
        <script>window.analytics = false;</script>
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
                <%block name="custom_banner_display"/>
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
        <%block name="footer"/>
    </div>

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

    <!--[if lt IE 9]>
        <script type="text/javascript">
            showErrorMessageUnsafe("<p>The AeroFS web portal does not fully support Internet Explorer version 8 or earlier. You might experience degraded styling or broken functionality.</p>");
        </script>
    <![endif]-->

    <%block name="scripts"/>

</body>
</html>
