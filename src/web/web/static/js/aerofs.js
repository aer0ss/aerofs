
function showErrorMessage(message) {
    cancelSlideUpTimer();
    var $bar = $("#message_bar");
    $bar.slideUp("normal", "easeInOutBack", function() {
        $bar.empty();
        $bar.append($('<div></div>')
            .addClass('flash_message error_message')
            .text(normalize(message)));
        $bar.slideDown("normal", "easeInOutBack", function() {
            delayedSlideUp($bar);
        });
    });
}

function showSuccessMessage(message) {
    cancelSlideUpTimer();
    var $bar = $("#message_bar");
    $bar.slideUp("normal", "easeInOutBack", function() {
        $bar.empty();
        $bar.append($('<div></div>')
            .addClass('flash_message success_message')
            .text(normalize(message)));
        $bar.slideDown("normal", "easeInOutBack", function() {
            delayedSlideUp($bar);
        });
    });
}

var slideUpTimer;

function cancelSlideUpTimer() {
    if (slideUpTimer) window.clearTimeout(slideUpTimer);
}

// Slide the bar up in 4 seconds
function delayedSlideUp($bar) {
    slideUpTimer = window.setTimeout(function() {
        $bar.slideUp("normal", "easeInOutBack");
    }, 4000);
}

function normalize(message) {
    message = message.charAt(0).toUpperCase() + message.slice(1);
    var last = message.slice(-1);

    if (last != '.' && last != '?' && last != '!') {
        message += '.';
    }
    return message;
}

function showErrorMessageFromResponse(xhr) {
    if (xhr.status == 400) {
        // We only use 400 for expected JSON error replies
        showErrorMessage($.parseJSON(xhr.responseText).message);
    } else if (xhr.status == 403) {
        window.location.href = "/login?next=" + encodeURIComponent(document.URL);
    } else {
        showErrorMessage("An error occurred processing your request." +
            " Please try again later. Contact support@aerofs.com if the" +
            " problem persists.");
        console.log("status " + xhr.status + " " + xhr.statusText +
            ": responseText: " + xhr.responseText);
    }
}

function getErrorTypeNullable(xhr) {
    return xhr.status == 400 ? $.parseJSON(xhr.responseText).type : null;
}
