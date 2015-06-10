// Convert newlines to line breaks
function nltobr(message) {
  'use strict';
  return message.replace(/\n/g, '<br>');
}

// Ugly: use the DOM to escape HTML
function escapify(message) {
  'use strict';
  var $node;
  var escapedMessage;
  $('body').append('<div id="dummy-node" style="display: none;"></div>');
  $node = $('#dummy-node');
  escapedMessage = $node.text(message).html();
  $node.remove();
  return escapedMessage;
}

var successMessageTimer;

function hideAllMessages() {
  'use strict';
  $('#flash-msg-success').hide(0);
  $('#flash-msg-error').hide(0);
  if (successMessageTimer) window.clearTimeout(successMessageTimer);
}

function normalizeMessage(message) {
  'use strict';
  message = message.charAt(0).toUpperCase() + message.slice(1);
  var last = message.slice(-1);

  if (last !== '.' && last !== '?' && last !== '!' && last !== '>') {
    message += '.';
  }
  return message;
}

// HTML code is allowed in the message
function showErrorMessageUnnormalizedUnsafe(message) {
  'use strict';
  hideAllMessages();
  $('#flash-msg-error-body').html(message);
  window.scrollTo(0,0);
  $('#flash-msg-error').fadeIn();
}

function showErrorMessageUnsafe(message) {
  'use strict';
  showErrorMessageUnnormalizedUnsafe(normalizeMessage(message));
}

function showErrorMessageUnnormalized(message) {
  'use strict';
  showErrorMessageUnnormalizedUnsafe(nltobr(escapify(message)));
}

function showErrorMessage(message) {
  'use strict';
  showErrorMessageUnnormalized(normalizeMessage(message));
}

// HTML code is allowed in the message
function showSuccessMessageUnsafe(message) {
  'use strict';
  var $msg;
  hideAllMessages();
  $msg = $('#flash-msg-success');
  $msg.html(normalizeMessage(message));
  window.scrollTo(0,0);
  $msg.fadeIn();

  // Fade out the message in 8 seconds
  successMessageTimer = window.setTimeout(function() {
    $msg.fadeOut();
  }, 8000);
}

function showSuccessMessage(message) {
  'use strict';
  showSuccessMessageUnsafe(nltobr(escapify(message)));
}

// hides all modal elements in the page
// related: hideAllModalsExceptThis()
function hideAllModals() {
  'use strict';
  $('div.modal').modal('hide');
}

// hides all modal elements in the page except for 'this'
// Caveat: the 'this' element needs to be set to the desired element to exclude.
// related: hideAllModals()
function hideAllModalsExceptThis() {
  $('div.modal').not(this).modal('hide');
}

// hide messages when a modal pops up
// hide all other modals when a modal is about to show
$(document).ready(function() {
  'use strict';
  $('div.modal').on('shown.bs.modal', hideAllMessages);
  // Using hideAllModals here triggers an edge case for modal dialogs with
  // tooltips. jQuery's impl. of tooltip means that a show event will be
  // triggered for the modal dialog, and using hideAllModals will cause the
  // modal dialog itself to close when any tooltips are to be shown.
  // This case occurs on the shared folder members dialog.
  $('div.modal').on('show.bs.modal', hideAllModalsExceptThis);
});

function getInternalErrorText() {
  'use strict';
  return 'Our server ran into an unexpected error while it was trying ' +
    'to serve this page. ' +
    'Please refresh the page and try again. ' +
    'If this issue persists, please contact ' +
    '<a href="mailto:support@aerofs.com" target="_blank">' +
    'support@aerofs.com</a> for assistance.';
}

function showErrorMessageFromResponse(xhr) {
  'use strict';
  var data;
  var status = xhr.status;
  if (xhr.responseText) {
    data = $.parseJSON(xhr.responseText);
  }
  if (status === 400) {
    // We only use 400 for expected JSON error replies. See error.py
    showErrorMessage(data.message);
  } else if (status === 403) {
    // See error_view.py:_force_login on generation of 403
    // Note that both web and bunker uses 'login' as the login route
    window.location.assign('/login?next=' +
      encodeURIComponent(window.location.pathname +
        window.location.search + window.location.hash));
  } else {
    showErrorMessageUnsafe(getInternalErrorText());
    console.log('show error message. status: ' + status +
      ' data: ' + data);
  }
}

// N.B. this message should match the text in the iOS app. Make sure to keep
// them in-sync.
function getClientsOfflineErrorText() {
    'use strict';
    var supportUrl = 'https://support.aerofs.com/hc/en-us/articles/202492734';
    return "<p>Your AeroFS clients are not currently reachable from the web.</p>" +
        "<p>To access your files from this page, please make sure at least one " +
        "of your AeroFS desktop clients or your organization's Team Server is " +
        "online and <a href='" + supportUrl + "' target='_blank'>" +
        "has API access enabled</a>.</p>";
}

function getErrorTypeNullable(xhr) {
  'use strict';
  return xhr.status === 400 ? $.parseJSON(xhr.responseText).type : null;
}

// precondition: the xhr must be an AeroFS error response
function getAeroFSErrorMessage(xhr) {
  'use strict';
  return $.parseJSON(xhr.responseText).message;
}

function setVisible($elem, visible) {
  'use strict';
  if (visible) $elem.removeClass('hidden');
  else $elem.addClass('hidden');
  return $elem;
}

function setEnabled($elem, enabled) {
  'use strict';
  // attribute 'disabled' is for form buttons.
  if (enabled) $elem.removeAttr('disabled').removeClass('disabled');
  else $elem.attr('disabled', 'disabled').addClass('disabled');
  return $elem;
}

function disableEscapingFromModal($modal) {
  'use strict';
  // For all the modals on this page, prevent ESC or mouse clicking on the
  // background to close the modal.
  // See http://stackoverflow.com/questions/9894339/disallow-twitter-bootstrap-modal-window-from-closing
  $modal.modal({
    backdrop: 'static',
    keyboard: false,
    show: false
  });
}

// add convenience method to POST raw JS objects as JSON bodies
$.extend({
  postJSON: function(url, data) {
    'use strict';
    return $.ajax({
      type: 'POST',
      url: url,
      data: JSON.stringify(data),
      dataType: 'json',
      contentType: 'application/json; charset=utf-8',
      processData: false
    });
  }
});

// the intended usage is to have a file selector for users to upload a file,
// and a hidden input whose value is the file content.
function linkFileSelectorToField(selector, field) {
  'use strict';
  $(selector).on('change', function() {
    var file = this.files[0];
    if (file) {
      var reader = new FileReader();
      reader.onload = function() {
        $(field).val(this.result);
      };
      // N.B. IE 11 doesn't support readAsBinaryString() despite what
      //   MSDN doc claims. Since we use this mostly to upload
      //   certificate and key files, reading the file as UTF-8 text
      //   is good enough.
      reader.readAsText(file, 'UTF-8');
    } else {
      $(field).val('');
    }
  });
}
