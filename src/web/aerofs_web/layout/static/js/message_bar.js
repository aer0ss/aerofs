$("#message_bar").slideDown("normal", "easeInOutBack");

$("#message_bar").click(function() {
    $(this).slideToggle("normal", "easeInOutBack", function() {
        $("#empty_message_bar").slideToggle("normal", "easeInOutBack");
    });
});

$("#empty_message_bar").click(function() {
    $(this).slideToggle("normal", "easeInOutBack", function() {
        $("#message_bar").slideToggle("normal", "easeInOutBack");
    });
});

function showErrorMessage(message) {
    $("#empty_message_bar").slideUp("normal", "easeInOutBack");
    var message_bar = $("#message_bar");
    message_bar.slideUp("normal", "easeInOutBack", function() {
        message_bar.html("<div class='flash_message error_message'>" +
            message + "</div>");
        message_bar.slideDown("normal", "easeInOutBack").delay(4000); // force error messages to stay visible for at least 4 seconds
    });
}

function showSuccessMessage(message) {
    $("#empty_message_bar").slideUp("normal", "easeInOutBack");
    var message_bar = $("#message_bar");
    message_bar.slideUp("normal", "easeInOutBack", function() {
        message_bar.html("<div class='flash_message success_message'>" +
            message + "</div>");
        message_bar.slideDown("normal", "easeInOutBack", function() {
            message_bar.delay(10000).slideUp("normal", "easeInOutBack"); // after 10 seconds hide success messages
        });
    });
}
