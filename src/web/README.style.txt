Buttons
----

o Use ".text-error" for dialog titles that indicates errors. When using modal.mako, define the function caller.error().

o Use btn-primary only for positive features, e.g. add a user, join a team, etc. Don't use it for negative features like removing users and leaving a team. Use btn instead, or btn-danger when appropriate.

o Disable buttons on form submission:
            $('#foo-form').submit(function() {
                setEnabled($("#submit-foo-button"), false);
                return true;
            });

o Only capitalize the first letter for dialog titles. Do not use CamelCase.
