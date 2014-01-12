Welcome to RockLog
==================

Hi, this is where the RockLog server-side source code lives.

RockLog is our framework for logging events from the clients in a central place
on our servers.

RockLog was designed to give us:

 - visibility: know what's happening on the clients in real time
 - flexibility: log whatever information you want, not just "defects"
 - powerful search ability over the events

It replaces the old "SV defects + Sentry" framework


Architecture
============

RockLog clients: Construct a JSON object representing the event and post it the
                 RockLog server.

RockLog server:  (this module) A minimal Python/Flask website served over NginX
                 that processes the request and indexes the JSON object
                 in ElasticSearch.

ElasticSearch:   A no-sql database especially good at search.
                 http://elasticsearch.org

Kibana:          A ruby web frontend for ElasticSearch. (http://kibana.org)


Running it locally
==================

If you want to run the RockLog server locally, here's what you need to do:

 1. Install ElasticSearch
    $ brew install elasticsearch

    When this is done, open http://localhost:9200/ to make sure ElasticSearch is
    running correctly.

 2. Install Kibana - follow the instructions at https://github.com/rashidkpc/Kibana

    Since we're not using logstash, we have to do a little change in the config.
    In KibanaConfig.rb, set Smart_index_pattern = 'defects-%Y-%m-%d'

    Browse to http://localhost:5601/ to check if Kibana is running

 3. Run RockLog
    Prerequisites: Python, pip, virtualenv, Ruby

    $ virtualenv ~/env
    $ ~/env/bin/pip install flask pyelasticsearch mysql-python
    $ cd ~/repos/aerofs/packaging/rocklog/opt/rocklog
    $ ~/env/bin/python rocklog.py

From now on, RockLog will be listening to requests on port 5000, processing
them, and saving the events in ElasticSearch.
