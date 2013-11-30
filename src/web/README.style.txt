Buttons
----

o Use ".text-error" for dialog titles that indicates errors. When using modal.mako, define the function caller.error().

o Use btn-primary only for positive features, e.g. add a user, join a team, etc. Don't use it for negative features like removing users and leaving a team. Use btn instead, or btn-danger when appropriate.

o Dialog title: Use <h4> for all dialog titles. Only capitalize the first letter of the entire title. Do NOT use Camel case.

o Button label: Use Camel Case, e.g. "Delete User", unless for long phrase, e.g. "List me all the things"

o Disable buttons on form submission:
            $('#foo-form').submit(function() {
                setEnabled($("#submit-foo-button"), false);
                return true;
            });

o Only capitalize the first letter for dialog titles. Do not use CamelCase.
