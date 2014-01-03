(function() {
  $(document).ready(function() {

    // Automatically include an X-CSRF-Token header on all
    // potentially-state-changing AJAX requests.  This way, we can have all the
    // CSRF handling in one place, rather than having to remember to add it
    // every time you call $.post()
    $.ajaxSetup({
        beforeSend: function(xhr, settings) {
            var csrftoken = $('meta[name=csrf-token]').attr('content');
            if (!/^(GET|HEAD|OPTIONS|TRACE)$/i.test(settings.type)) {
                xhr.setRequestHeader("X-CSRF-Token", csrftoken);
            }
        }
    });
  });
}).call(this);
