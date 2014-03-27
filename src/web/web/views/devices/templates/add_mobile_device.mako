<%inherit file="dashboard_layout.mako"/>

<%!
    from web.util import is_private_deployment
    from pyramid.security import authenticated_userid
%>

<%! page_title = "AeroFS for Mobile Devices" %>

<h2>AeroFS for Mobile Devices</h2>
<p>Ready to set up AeroFS on your mobile device?</p>

%if is_private_deployment(request.registry.settings) or authenticated_userid(request).endswith('@aerofs.com'):

    ########## Private cloud ##########

    <h4>1. Get the app</h4>
    <div class="row">
        <div class="span2" style="text-align: center;">
            <a href="https://itunes.apple.com/us/app/aerofs-for-private-cloud/id778103731?mt=8" target="_blank">
                <img alt="AeroFS on the App Store" src="https://linkmaker.itunes.apple.com/htmlResources/assets/en_us//images/web/linkmaker/badge_appstore-lrg.png"/>
            </a>
        </div>
        <div class="span2" style="text-align: center;">
            <em style="line-height: 40px;">Android: coming soon</em>
            ## TODO: Use this when we release the Android app for Private Cloud
            ##<a href="https://play.google.com/store/apps/details?id=com.aerofs.android2" target="_blank">
            ##  <img alt="AeroFS on Google Play" src="https://developer.android.com/images/brand/en_generic_rgb_wo_45.png" />
            ##</a>
        </div>
    </div>
    <br>

    <h4>2. Launch the app and scan the QR code</h4>
    <p>Launch the app. When instructed, use the button below to generate a QR code for your device:</p>
    <p>
        <a href="#" onclick="getQRCode(); return false;" class="btn btn-primary btn-lg" role="button">Get QR Code</a>
    </p>
    <div id="result"></div>


    <%block name="scripts">
        <script type="text/javascript">

            var countdownTimer;
            var expirationDate;

            function getQRCode() {
                ## Create a new image
                var img = $('<img/>');
                img.attr('src', "${qrcode_url}");
                img.error(function () {
                    showErrorMessage(getInternalErrorText());
                    console.log("failed to load: " + $(this).attr('src'));
                });

                expirationDate = Date.now() + 3 * 60 * 1000
                var countdown = $('<p>Valid for <span id="timer">03:00</span></p>');

                $('#result').empty();
                countdown.appendTo('#result');
                img.appendTo('#result');
                doCountdown();
            }

            ## pads a number with a leading zero if needed
            function pad(n) {
                return n < 10 ? '0' + n : n;
            }

            function doCountdown() {
                var secondsRemaining = Math.round((expirationDate - Date.now()) / 1000);
                if (secondsRemaining > 0) {
                    $('#timer').text(pad(Math.floor(secondsRemaining / 60)) + ':' + pad(secondsRemaining % 60))
                    clearTimeout(countdownTimer);
                    countdownTimer = setTimeout(doCountdown, 1000)
                } else {
                    $('#result').empty();
                }
            }
        </script>
    </%block>

%else:

    ########## Hybrid cloud ##########

    <h4>Android app</h4>
    <a href="https://play.google.com/store/apps/details?id=com.aerofs.android" target="_blank">
      <img alt="AeroFS on Google Play" src="https://developer.android.com/images/brand/en_generic_rgb_wo_45.png" />
    </a>
    <br/><br/>
    <h4>iOS app</h4>
    <p>
        The iOS app is only available for our Private Cloud users.
        <a href="${request.route_path('pricing')}">Learn more.</a>
    </p>

%endif