# How to run the test

First, install docker on the host computer. Then:

```
./run.sh <aws_access_key> <aws_secret_key>
```

The AWS keys should have the permission to launch and terminate EC2 instances
and security groups.

Caveat: the keys are easily readable by `ps` and other commands. Don't use
long-lived or production keys.

# How the test script works

`run.sh` performs the following tasks:

1. Build the three versions of the test app.
2. Push the app container images built in Step 1 to Docker Hub. The script uses
   the Docker Hub user name specified in  the command line.
3. Build and run the test driver container which does the remaining steps.
4. Provision an EC2 host with a public IP using Terraform. The host runs Ship
   Enterprise's cloud-config file generated in Step 1.
5. Test the in-place upgrade flow.
6. Clean up EC2 resources.

# Step 5 detail

There are three versions of the test app: `past`, `present`, and `future`. Each
version consists of three containers: Loader, Data, and Nginx. Nginx exposes
Loader's API to port 0.0.0.0:80. It also imports volumes from Data.

The Loader and Nginx containers are identical across all the three versions.
For Data containers, the `past` version defines a volume at `/data1` with a
file at `/data1/file`. The `present` version defines two empty volumes `/data1` 
and `/data2`. When it launches, it copies `/data1/file` to `/data2/file`. The
`future` version defines an empty volume `/data2`.

After upgrading the test app from `past` to `present` and then to `future`, the
test driver verifies that `/data2/file` exists with the expected content.
