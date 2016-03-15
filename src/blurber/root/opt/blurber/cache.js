var LfuMap = new require('collections/lfu-map'),
    CAPACITY = 16384;

module.exports = new LfuMap({}, CAPACITY);