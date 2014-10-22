#!/bin/sh

# get customer information (note that I don't have to supply any creds)
curl -v -XGET http://localhost:9999/customers/1 | jq .

# attempt to create a customer
# notice that I'm not allowed to do so!
curl -v -XPOST -H 'Content-Type: application/json' http://localhost:9999/customers -d '{"customer_name": "Allen George", "organization_name": "AeroFS", "seats": 5}' | jq .

# attempt to create a customer again, but this time pretend we're a valid user
# wanted to show different response types (int return)
curl -v -XPOST -H 'Authorization: Aero-Device-Cert 0d6033ec2d334e2795838e63d915479b test@aerofs.com' -H 'DName: G=test.aerofs.com/CN=dhljdipbkajcojmpmmkokeobmmafahbdfclipoklhffbnlfcennhdkdffgamlnbp' -H 'Verify: SUCCESS' -H 'Content-Type: application/json' http://localhost:9999/customers -d '{"customer_name": "Allen George", "organization_name": "AeroFS", "seats": 5}' | jq .

# get customer information again
# notice that you now get information
curl -v -XGET http://localhost:9999/customers/1 | jq .

# create a different customer
# but specify invalid number of seats (oops)
curl -v -XPOST -H 'Authorization: Aero-Device-Cert 0d6033ec2d334e2795838e63d915479b test@aerofs.com' -H 'DName: G=test.aerofs.com/CN=dhljdipbkajcojmpmmkokeobmmafahbdfclipoklhffbnlfcennhdkdffgamlnbp' -H 'Verify: SUCCESS' -H 'Content-Type: application/json' http://localhost:9999/customers -d '{"customer_name": "Jon Pile", "organization_name": "Pileomania", "seats": 500}' | jq .

# let's try create that customer again
# this time it succeeds
curl -v -XPOST -H 'Authorization: Aero-Device-Cert 0d6033ec2d334e2795838e63d915479b test@aerofs.com' -H 'DName: G=test.aerofs.com/CN=dhljdipbkajcojmpmmkokeobmmafahbdfclipoklhffbnlfcennhdkdffgamlnbp' -H 'Verify: SUCCESS' -H 'Content-Type: application/json' http://localhost:9999/customers -d '{"customer_name": "Jon Pile", "organization_name": "Pileomania", "seats": 50}' | jq .

# let's list the customers via the tasks interface
curl -v -XPOST http://localhost:8888/tasks/dump | jq .

# now, let's modify an existing customer
# notice that the customer information has changed
curl -v -XPOST -H 'Authorization: Aero-Device-Cert 0d6033ec2d334e2795838e63d915479b test@aerofs.com' -H 'DName: G=test.aerofs.com/CN=dhljdipbkajcojmpmmkokeobmmafahbdfclipoklhffbnlfcennhdkdffgamlnbp' -H 'Verify: SUCCESS' http://localhost:9999/customers/1?seats=10 | jq .

# now, let's modify an existing customer
# notice that the parameter is validated the same way and it is disallowd
curl -v -XPOST -H 'Authorization: Aero-Device-Cert 0d6033ec2d334e2795838e63d915479b test@aerofs.com' -H 'DName: G=test.aerofs.com/CN=dhljdipbkajcojmpmmkokeobmmafahbdfclipoklhffbnlfcennhdkdffgamlnbp' -H 'Verify: SUCCESS' http://localhost:9999/customers/1?seats=1000 | jq .

# get customer information again
# notice that you now get information
curl -v -XGET http://localhost:9999/customers/1 | jq .

# get list of metrics
# notice that we get notified how many times new customers were created
curl -v -XPOST http://localhost:8888/tasks/metrics | jq .
