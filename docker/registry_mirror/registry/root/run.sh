#!/bin/bash
set -e

cp /config.yml /etc/docker/registry/config.yml
registry serve /etc/docker/registry/config.yml
