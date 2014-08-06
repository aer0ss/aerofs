defaultSpinnerOptions = {
    lines: 11,
    length: 5,
    width: 1.6,
    radius: 4,
    corners: 1,
    rotate: 0,
    color: '#000',
    speed: 1,
    trail: 60,
    shadow: false,
    hwaccel: true,
    className: 'spinner',
    zIndex: 2e9,
    top: top,
    left: 0
};
function initializeSpinners() {
    $.fn.spin = function(opts) {
        this.each(function() {
            var $this = $(this), data = $this.data();

            if (data.spinner) {
                data.spinner.stop();
                delete data.spinner;
            }
            if (opts !== false) {
                data.spinner = new Spinner($.extend({color: $this.css('color')}, opts)).spin(this);
            }
        });
        return this;
    };
}

// @param top the top position. See http://fgnass.github.io/spin.js/
function startSpinner($spinner, top) {
    $spinner.spin(defaultSpinnerOptions);
}

function stopSpinner($spinner) {
    if ($spinner.data().spinner) $spinner.data().spinner.stop();
}