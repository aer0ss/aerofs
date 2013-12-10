<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Add Mobile Device" %>

<h2>Add Mobile Device</h2>

<p>Ready to set up AeroFS on your iPhone or iPad?</p>
<ol>
    <li>Install the app from the App Store.</li>
    <li>Launch the app and follow the instructions there.</li>
    <li>When instructed, come back to this page and use the buttons below to generate an access code for your device:</li>
</ol>
<p>

<a href="#" onclick="getQRCode();   return false;" class="btn btn-primary btn-lg" role="button" style="margin:0 16px 16px 0;">Get QR Code</a>
<a href="#" onclick="getTextCode(); return false;" class="btn btn-primary btn-lg" role="button" style="margin:0 16px 16px 0;">Get Text Code</a>

<div id="result"></div>


<%block name="scripts">
    <script type="text/javascript">

        function getQRCode() {
            ## Create a new image
            var img = $('<img/>');
            img.attr('src', "${qrcode_url}");
            img.error(function () {
                showErrorMessage(getInternalErrorText());
                console.log("failed to load: " + $(this).attr('src'));
            });
            showResult(img);
        }

        function getTextCode() {

            $.ajax({
                'url': '${textcode_url}',
                'type': 'GET',
                'success': function (code) {
                    ## Create a new textarea
                    var textarea = $('<textarea>');
                    textarea.attr('readonly', true).attr('rows', 6).attr('cols', 40).addClass('span5').text(code);
                    showResult(textarea);
                },
                'error': showErrorMessageFromResponse
            });
        }

        var countdownTimer;
        var expirationDate;

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

        function showResult(code) {
            $('#result').empty();

            expirationDate = Date.now() + 3 * 60 * 1000
            var countdown = $('<p>Valid for <span id="timer">03:00</span></p>');

            countdown.appendTo('#result');
            code.appendTo('#result');

            doCountdown();
        }

    </script>
</%block>
