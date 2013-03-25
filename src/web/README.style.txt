Buttons
----

o Use btn-primary only for positive features, e.g. add a user, join a team, etc. Don't use it for negative features like removing users and leaving a team. Use btn instead, or btn-danger when appropriate.

o Disable buttons on form submission:
            $('#foo-form').submit(function() {
                $("#submit-foo-button").attr("disabled", "disabled");
                return true;
            });
