'use strict';

let cache = require('./cache'),
    markdown = require('markdown').markdown,
    mime = require('mime'),
    og = require('open-graph'),
    request = require('request'),
    restify = require('restify'),
    server = restify.createServer();

let TIMEOUT = 35000,
    contentTypeRe = /^text\/html;?/;

let sendFromCache = (res, url) => {
        console.log(new Date().toISOString(), cache.length, url, 'retrieved from cache');
        res.send(cache.get(url));
    },
    sendFromNet = (res, url, blurb) => {
        console.log(new Date().toISOString(), cache.length, url, 'retrieved from net');
        cache.set(url, blurb);
        res.send(blurb);
    },
    sendError = (res, url, err, blurb) => {
        console.error(new Date().toISOString(), cache.length, url, err)
        cache.set(url, blurb);
        res.send(blurb);
    };

// middleware
server.use(restify.CORS());
server.use(restify.queryParser());

// endpoints
server.get('/url', function respond(req, res, next) {
    let url = req.query.id;
    res.header('Content-Disposition', 'inline');
    res.header('Content-Type', 'application/json');
    if (cache.has(url)) {
        sendFromCache(res, url);
    } else {
        request({
            url: url,
            method: 'HEAD',
            timeout: TIMEOUT
        }, (err, requestRes, body) => {
            let hasContentType = contentTypeRe.test(requestRes.headers['content-type']),
                hasMimeType = mime.lookup(url) === 'text/html';
            if (hasContentType || hasMimeType) {
                og(url, (err, blurb) => err ? sendError(res, url, err, {}) : sendFromNet(res, url, blurb));
            } else {
                sendError(res, url, 'refusing to parse non-html page', {});
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
        sendFromCache(res, url);
    } else {
        request({
            url: url,
            method: 'GET',
            timeout: TIMEOUT
        }).on('response', (err, requestRes, body) => {
            if (err) {
                sendError(res, url, err, '');
            } else if (requestRes.statusCode !== 200) {
                sendError(res, url, `${ requestRes.statusCode } status code - returning empty body`, '');
            } else {
                sendFromNet(res, url, (mimeType === 'text/x-markdown') ? markdown.toHTML(body) : body);
            }
        });
    }
});

server.get('/gist', function respond(req, res, next) {
    var url = `https://gist.github.com/${ req.query.id }.json`;
    res.header('Content-Disposition', 'inline');
    res.header('Content-Type', 'application/json');
    if (cache.has(url)) {
        sendFromCache(res, url);
    } else {
        request({
            url: url,
            method: 'GET',
            timeout: TIMEOUT
        }, (err, requestRes, blurb) => {
            if (err) {
                sendError(res, url, err, '');
            } else if (requestRes.statusCode !== 200) {
                sendError(res, url, `${ requestRes.statusCode } status code - returning empty body`, '');
            } else {
                sendFromNet(res, url, blurb);
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
        return res.end(204);
    } else {
        return res.end(new restify.MethodNotAllowedError());
    }
});

server.listen(8080, function () {
    console.log('%s listening at %s', server.name, server.url);
});
