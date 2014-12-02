/* Polyfills for features missing from IE 8 and below. */

Date.now = Date.now || function() { return +new Date; };
Array.prototype.indexOf = function(obj, start) {
     for (var i = (start || 0), j = this.length; i < j; i++) {
         if (this[i] === obj) { return i; }
     }
     return -1;
};
Array.prototype.filter = function filter(callback) {
    if (this === undefined || this === null) {
        throw new TypeError(this + 'is not an object');
    }

    if (!(callback instanceof Function)) {
        throw new TypeError(callback + ' is not a function');
    }

    var
    object = Object(this),
    scope = arguments[1],
    arraylike = object instanceof String ? object.split('') : object,
    length = Math.max(Math.min(arraylike.length, 9007199254740991), 0) || 0,
    index = -1,
    result = [],
    element;

    while (++index < length) {
        element = arraylike[index];

        if (index in arraylike && callback.call(scope, element, index, object)) {
            result.push(element);
        }
    }

    return result;
};