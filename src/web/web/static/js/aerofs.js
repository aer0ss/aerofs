// HTML code is allowed in the message
function showErrorMessageUnnormalized(message) {
    hideAllMessages();
    $('#flash-msg-error-body').html(message);
    $("#flash-msg-error").fadeIn();
}

function showErrorMessage(message) {
    showErrorMessageUnnormalized(normalizeMessage(message));
}

var successMessageTimer;

// HTML code is allowed in the message
function showSuccessMessage(message) {
    hideAllMessages();
    var $msg = $("#flash-msg-success");
    $msg.html(normalizeMessage(message));
    $msg.fadeIn();

    // Fade out the message in 8 seconds
    successMessageTimer = window.setTimeout(function() {
        $msg.fadeOut();
    }, 8000);
}

function hideAllMessages() {
    $("#flash-msg-success").hide(0);
    $("#flash-msg-error").hide(0);
    if (successMessageTimer) window.clearTimeout(successMessageTimer);
}

// hide messages when a modal pops up
$('div.modal').on('shown', hideAllMessages);

function fadeOutErrorMessage() {
    $("#flash-msg-error").fadeOut();
}

function normalizeMessage(message) {
    message = message.charAt(0).toUpperCase() + message.slice(1);
    var last = message.slice(-1);

    if (last != '.' && last != '?' && last != '!') {
        message += '.';
    }
    return message;
}

function showErrorMessageFromResponse(xhr) {
    if (xhr.status == 400) {
        // We only use 400 for expected JSON error replies. See error.py
        showErrorMessage(getAeroFSErrorMessage(xhr));
    } else if (xhr.status == 403) {
        // See error_view.py:_force_login on generation of 403
        window.location.assign("/login?next=" +
            encodeURIComponent(window.location.pathname +
                window.location.search + window.location.hash));
    } else {
        showErrorMessage(getInternalErrorText());
        console.log("show error message. status: " + xhr.status +
            " statusText: " + xhr.statusText + " responseText: " + xhr.responseText);
    }
}

function getInternalErrorText() {
    return "Sorry! An error has occurred. Please refresh" +
        " this page and try again. If this issue persists contact" +
        " <a href='mailto:support@aerofs.com' target='_blank'>" +
        "support@aerofs.com</a> for assistance."
}

function getErrorTypeNullable(xhr) {
    return xhr.status == 400 ? $.parseJSON(xhr.responseText).type : null;
}

// precondition: the xhr must be an AeroFS error response
function getAeroFSErrorMessage(xhr) {
    return $.parseJSON(xhr.responseText).message;
}

// precondition: the xhr must be an AeroFS error response
function getAeroFSErrorData(xhr) {
    return $.parseJSON(xhr.responseText).data;
}

function setVisible($elem, visible) {
    if (visible) $elem.removeClass("hidden");
    else $elem.addClass("hidden");
    return $elem;
}

function setEnabled($elem, enabled) {
    // attribute 'disabled' is for form buttons.
    if (enabled) $elem.removeAttr("disabled").removeClass('disabled');
    else $elem.attr("disabled", "disabled").addClass('disabled');
    return $elem;
}

function disableEsapingFromModal($modal) {
    // For all the modals on this page, prevent ESC or mouse clicking on the
    // background to close the modal.
    // See http://stackoverflow.com/questions/9894339/disallow-twitter-bootstrap-modal-window-from-closing
    $modal.modal({
        backdrop: 'static',
        keyboard: false,
        show: false
    });
}

function hideAllModals() {
    $('div.modal').modal('hide');
}

// add convenience method to POST raw JS objects as JSON bodies
$.extend({
    postJSON: function(url, data) {
        return $.ajax({
            type: "POST",
            url: url,
            data: JSON.stringify(data),
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            processData: false
        });
    }
});
