# recognize third party deps in vendor/ dir
# must use go v1.5 or greater
export GO15VENDOREXPERIMENT = 1

include ../../Makefile.base

IMAGE_NAME    = aeroim/trifrost
PACKAGE_NAME   = aerofs.com/trifrost

GOCKERIZE_CTX = ../../

trifrost: *.go
	go build

clean:
	rm -f ./trifrost

