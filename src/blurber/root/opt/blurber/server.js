var cache = require('./cache'),
    markdown = require('markdown').markdown,
    og = require('open-graph'),
    mime = require('mime'),
    request = require('request'),
    restify = require('restify'),
    util = require('util'),
    server = restify.createServer();

// middleware
server.use(restify.CORS());
server.use(restify.queryParser());

// endpoints
server.get('/url', function respond(req, res, next) {
    var url = req.query.id;
    res.header('Content-Disposition', 'inline');
    res.header('Content-Type', 'application/json');
    if (cache.has(url)) {
        res.send(cache.get(url).blurb);
    } else {
        og(url, function (err, blurb) {
            if (err) {
                console.error(err);
                res.send();
            } else {
                cache.set(url, { blurb: blurb, date: new Date(), url: url });
                res.send(blurb);
            }
        });
    }
});

server.get('/text', function respond(req, res, next) {
    var url = req.query.id,
        mimeType = mime.lookup(url),
        contentType = (mimeType === 'text/x-markdown') ? 'text/html' : mimeType;
    res.header('Content-Disposition', 'inline');
    res.header('Content-Type', contentType);
    if (cache.has(url)) {
        res.end(cache.get(url).blurb);
    } else {
        request(url, function (err, requestRes, body) {
            var blurb = (mimeType === 'text/x-markdown') ? markdown.toHTML(body) : body;
            if (err) {
                console.error(err);
                res.send();
            } else if (requestRes.statusCode !== 200) {
                res.send();
            } else {
                cache.set(url, { blurb: blurb, date: new Date(), url: url });
                res.end(blurb);
            }
        });
    }
});

server.get('/gist', function respond(req, res, next) {
    var url = util.format('https://gist.github.com/%s.json', req.query.id);
    res.header('Content-Disposition', 'inline');
    res.header('Content-Type', 'application/json');
    if (cache.has(url)) {
        res.end(cache.get(url).blurb);
    } else {
        request(url, function (err, requestRes, blurb) {
            if (err) {
                console.err(err);
                res.send();
            } else if (requestRes.statusCode !== 200) {
                res.send();
            } else {
                cache.set(url, { blurb: blurb, date: new Date(), url: url });
                res.end(blurb);
            }
        });
    }
});

// start server
server.on('MethodNotAllowed', function unknownMethodHandler(req, res) {
    var allowHeaders = ['Accept', 'Accept-Version', 'Api-Version', 'Cache-Control', 'Content-Type'];
    if (req.method.toLowerCase() === 'options') {
        if (res.methods.indexOf('OPTIONS') === -1) {
            res.methods.push('OPTIONS');
        }
        res.header('Access-Control-Allow-Credentials', true);
        res.header('Access-Control-Allow-Headers', allowHeaders.join(', '));
        res.header('Access-Control-Allow-Methods', res.methods.join(', '));
        res.header('Access-Control-Allow-Origin', '*');
        return res.send(204);
    } else {
        return res.send(new restify.MethodNotAllowedError());
    }
});

server.listen(8080, function () {
    console.log('%s listening at %s', server.name, server.url);
});
