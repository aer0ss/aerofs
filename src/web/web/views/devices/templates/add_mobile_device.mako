<%inherit file="dashboard_layout.mako"/>

<%! page_title = "AeroFS for Mobile Devices" %>

<%!
    from web.util import is_mobile_disabled
%>

<%
    support_email = request.registry.settings.get("base.www.support_email_address", False)
%>

<div class="page-block">
    <h2>AeroFS for Mobile Devices</h2>
    %if is_mobile_disabled(request.registry.settings):
        <p>Your administrator has disabled mobile access to AeroFS.</p>
        <br/>
        <p>Please contact ${support_email} with any requests for mobile access to AeroFS.</p>
    %else:
        <p>Ready to set up AeroFS on your mobile device?</p>
    %endif
</div>

%if not is_mobile_disabled(request.registry.settings):

<ol>
<li class="page-block">
    <h4>Get the app</h4>
    <a href="https://itunes.apple.com/us/app/aerofs-for-private-cloud/id778103731?mt=8" target="_blank">
        <img alt="AeroFS on the App Store" width=135px height=40px src="https://linkmaker.itunes.apple.com/htmlResources/assets/en_us/images/web/linkmaker/badge_appstore-lrg.png"/>
    </a>
    <span style="margin: auto 8px">or</span>
    <a href="https://play.google.com/store/apps/details?id=com.aerofs.android" target="_blank">
      <img alt="AeroFS on Google Play" width=135px height=40px src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" />
    </a>
</li>

<li class="page-block">
    <h4>Launch the app and scan the QR code</h4>
    <p>Launch the app. When instructed, use the button below to generate a QR code for your device:</p>
    <p>
        <a href="#" onclick="getQRCode(); return false;" class="btn btn-primary btn-lg" role="button">Get QR Code</a>
    </p>
    <div id="result"></div>
</li>
</ol>

%endif

<%block name="scripts">
    <script>
        var countdownTimer;
        var expirationDate;

        function getQRCode() {
            ## Create a new image
            var img = $('<img/>');
            img.attr('src', '${qrcode_url}');
            img.error(function () {
                showErrorMessageUnsafe(getInternalErrorText());
                console.log("failed to load: " + $(this).attr('src'));
            });

            expirationDate = Date.now() + 3 * 60 * 1000;
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
