DEST = $(abspath ../../../../resource/updater)
SRC = $(wildcard *.go)

COMMIT = $(shell git rev-parse HEAD)
TIMESTAMP = $(shell date -u '+%Y-%m-%dT%H:%M:%SZ')
BUILD_FLAGS = -a -installsuffix static
LDFLAGS = -s -w -X main.buildtime=$(TIMESTAMP) -X main.commithash=$(COMMIT)
GOPATH = $(abspath ../../../)
GOBUILD = GOPATH=$(GOPATH) CGOENABLED=0 go build $(BUILD_FLAGS)


all: $(DEST)/linux/updater-i386 \
	 $(DEST)/linux/updater-amd64 \
	 $(DEST)/osx/updater \
	 $(DEST)/win/updater.exe

$(DEST)/linux/updater-amd64: $(SRC)
	GOOS=linux GOARCH=amd64 $(GOBUILD) -ldflags "$(LDFLAGS)" -o "$@" aerofs.com/updater

$(DEST)/linux/updater-i386: $(SRC)
	GOOS=linux GOARCH=386 $(GOBUILD) -ldflags "$(LDFLAGS)" -o "$@" aerofs.com/updater

$(DEST)/osx/updater: $(SRC)
	GOOS=darwin GOARCH=amd64 $(GOBUILD) -ldflags "$(LDFLAGS)" -o "$@" aerofs.com/updater

$(DEST)/win/updater.exe: $(SRC)
	# NB: do not store icon w/ .syso ext to prevent it from being picked up in *nix build
	cp icon icon.syso
	GOOS=windows GOARCH=386 $(GOBUILD) -ldflags "$(LDFLAGS) -H windowsgui" -o "$@" aerofs.com/updater
	rm icon.syso
