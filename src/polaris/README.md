# Polaris

Polaris is AeroFS' centralized version-metadata server.

# API

## Methods

Polaris exposes the following routes:

### POST   /batch/transforms

### POST   /batch/locations

### POST   /objects/{oid}

### GET    /objects/{oid}/versions/{version}/locations/{did}

### POST   /objects/{oid}/versions/{version}/locations/{did}

### DELETE /objects/{oid}/versions/{version}/locations/{did}

### GET    /transforms/{oid}

## Types

## TODO

* Have a validator that checks not null