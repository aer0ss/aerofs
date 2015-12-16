var cache = new require('collections/dict')(),
    ONE_HOUR = 3600000,
    ONE_WEEK = 604800000;

setInterval(function (maxDiff) {
    var date = new Date();
    cache.deleteEach(
        cache.filter(function (value) {
            return date - value.date > maxDiff;
        }).map(function (value) {
            return value.url;
        })
    );
}, ONE_HOUR, ONE_WEEK);

module.exports = cache;