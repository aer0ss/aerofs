# recognize third party deps in vendor/ dir
# must use go v1.5 or greater
export GO15VENDOREXPERIMENT = 1

include ../../Makefile.base

IMAGE_NAME = aeroim/sloth
PACKAGE_NAME = aerofs.com/sloth

GOCKERIZE_CTX = ../../..

sloth: $(shell find . -name '*.go')
	GOPATH=$(abspath $(GOCKERIZE_CTX)) go build

clean:
	rm -f ./sloth
