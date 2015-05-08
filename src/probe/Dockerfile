FROM google/golang
COPY builder /
RUN CGO_ENABLED=0 go get -a -x -installsuffix cgo -ldflags '-s -w' probe
CMD docker build -t aerofs/probe /gopath
