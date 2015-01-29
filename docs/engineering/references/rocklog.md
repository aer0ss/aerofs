# RockLog - defect reporter, aggregator, and query interface

## Introduction

Although the scope has changed somewhat since the initial proposal, the purpose of RockLog is:
	...provide a dynamically-growable, nearly zero-configuration interface to report
	query, and visualize client-side defects.

The word "defect" needs some definition here: a defect is an event reported by an AeroFS
client. It could be an autobug, a priority problem report, or an explicitly-reported event
that was posted using the RockLog client API.


## Server components and setup

### RockLog

RockLog itself is a small python server. It implements a route for POST at `/defects` that
simply retraces the defect from the JSON and saves the unobfuscated content to ElasticSearch.

Location on server: `/opt/rocklog/`

Log location: `/var/log/aerofs/rocklog.log`

Source: `src/rocklog/`

[ see Tornado for server environment ]


## Tornado server

Tornado is a tiny python application server. It wraps access to the rocklog app.

Listener: port `8000`

Log location: None? This is actually super annoying.

Location on server: `/opt/rocklog/tornado_server.py`

Source: `src/rocklog/`

Control: `service tornado {stop|start|restart}`


## Retrace server

This is a small Java server that wraps access to the ProGuard Retrace tool.

Listener: port `50123`

Source: https://github.com/aerofs/RetraceServer

Control: `service retrace_server {stop|start|restart}`


## ElasticSearch

A no-sql database with a RESTful interface listening on port 9200.

ElasticSearch is administered through a REST API - including health-checks, deleting 
corrupted indexes, etc. See http://www.elasticsearch.org/guide/reference/api/ for the full API.

Installed via apt-get. No site-specific configuration required.

Listener: port `9200`

Log location: `/var/log/elasticsearch/`

Location on server: `/usr/share/elasticsearch/`

Control: `service elasticsearch {stop|start|restart}`

Configuration on server: `/etc/elasticsearch/elasticsearch.yml`

## Nginx

Nginx is set up to to do two things:

 - proxy port 80, used to post defects to rocklog.
 
 - proxy port 443, used to query kibana from within the VPN.
 
See below for notes on a temporary service for testing Kibana-3.

Listener: see above

Log location: `/var/log/nginx/`

Location on server: `/var/lib/nginx/`

Control: `service nginx {stop|start|restart}`

Configuration on server: `/etc/nginx/nginx.conf`  `/etc/nginx/conf.d/`

## Kibana-2

Written in Ruby. See GUnicorn for the server environment.

IMPORTANT NOTE: Kibana's download file name does not change when they update their product, it's always 0.2.0. And we need a bugfix that they haven't shipped yet - see the puppet init script for details. The symptom of the bug is that the display never completes, this is because timezone.js is shipped as text and modern browsers won't execute text.

Location on server: `/opt/kibana/` (symlink to /opt/Kibana-0.2.0)

Source: http://kibana.org/

Server environment: see Unicorn

## Unicorn

Unicorn is an HTTP server for Ruby apps. It starts in /opt/rocklog and serves up the Ruby-implemented Kibana-2. Mostly it gets left alone.

Listener: port `8080` (exposed by nginx at :443)

Location on server: `/usr/local/bin`

Control: unicorn.conf in /etc/init

Ref: http://unicorn.bogomips.org/

# Sanity tests

ElasticSearch: 
	
	curl http://rocklog.aerofs.com:9200
	#Should return something like "You know, for search".
	
	curl -XGET 'http://localhost:9200/_cluster/health?pretty=true'

Defect-publishing test:

	curl -X POST \ 
		-H "Accept: application/json" -H "Content-type: application/json" \
		-d " {\"@message\":\"hi mom\", \"@timestamp\":\"$(date -u '+%Y-%m-%dT%H:%M:%S%z')\"}" \
		http://rocklog.aerofs.com:80/defects

	# you can then observe the "hi mom" defect in rocklog (assuming everything is working)


## Initial setup and configuration

AFAICT puppet is able to completely provision and start up the RockLog suite.


## Disk and other resources

ElasticSearch is written to /data/ , which should be sufficiently large for a lot of
defects.

The indexes in ElasticSearch are cleaned on a daily basis by the es_cleaner.py script,
currently set to drop indexes older than 90 days.


## Firewall rules

The RockLog stack requires the following, which are all accomplished using 
EC2 security groups:

- incoming port 80 is open to the public, on a publicly visible elastic IP. This is used
by clients to POST defects to rocklog. No other activity.
 
- incoming port 443 is open to the VPN only. This is used to view Kibana from inside
the VPN.
 
- all other ports are blocked to incoming traffic.
 
 
## Kibana-3

Kibana3 (http://three.kibana.org) offers all the existing features but with updated
look-and-feel options, and does so without Ruby. It's purely HTML/JavaScript - the 
client browser talks directly to ElasticSearch. Using Kibana3 would allow us to drop
several server component, but it will require exposing rocklog's ElasticSearch port to the
VPN. 

Currently the following changes have been applied locally for testing: firewall rule _is_ in place; a preview is at: https://rocklog.aerofs.com:4443/
You will most likely need to find the configuration button for "timepicker" and set the
ES search value to: 
	
	[defects-]YYYY-MM-DD

This isn't quite ready for primetime, but should be soon.

The following changes are applied to rocklog but not in master:

- incoming port 4443 is open to the VPN only (web host for kibana 3)

- incoming port 9200 is open to the VPN only (ElasticSearch)

- nginx has a web server that is listening on port 4443 and serves pages from
`/opt/kibana3/kibana-master`

- Kibana-3 is untarred under `/opt/kibana3/`


## Posting Defects

RockLog is well-suited to adding instrumentation to the client for an event you would
like to collect and track. It's easy and fun, why not send some defects today?

	rockLog.newDefect("system.classnotfound")
		.setException(e)
		.send();
	
	rockLog.newDefect("daemon.empty_filename")
		.setMessage("Oh noes!)
		.send();

Some hints for naming defects if you use this:

- Short string that describes what component failed or condition occurred.

- Use dots to create hierarchies.

Good names: "daemon.linker.someMethod", "system.nolaunch"

Bad names: "Name With Spaces", "daemon.linker.someMethod_failed" <-- "failed" is redundant


## Notes

UWSGI is not needed, though it was in the past. So shut it down if you see it running. Ditto GUnicorn (a python implementation similar to Unicorn, which we do use).


# Debugging tricks

Try stopping the Tornado service and running it from the command line:

    python /opt/rocklog/tornado_server.py

Then try the sanity-check. If tornado/rocklog is hitting a problem, you will at least see the output to the console.

# Future development

Client metrics have historically been in-scope for rocklog. In the past, metrics were
aggregated within the client (using yammer Metrics) and sent to rocklog.aerofs.com every
10 minutes. These metrics were then forwarded to Graphite and squashed into the local
ElasticSearch data store.

This is currently disabled in the client (pending push) and short-circuited in the server (see rocklog.py). At some point, when the older clients have all dropped off, we can remove the /metrics path in rockloyg.py.

Tornado's init script needs to redirect the stdout/stderr somewhere useful.