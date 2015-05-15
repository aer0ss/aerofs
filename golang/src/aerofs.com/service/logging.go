package service

import (
	"fmt"
	"net/http"
	"sync/atomic"
)

type ProxyResponseWriter struct {
	n          uint32
	w          http.ResponseWriter
	StatusCode int
}

func (w *ProxyResponseWriter) Header() http.Header { return w.w.Header() }
func (w *ProxyResponseWriter) Write(d []byte) (int, error) {
	if w.StatusCode == 0 {
		w.WriteHeader(http.StatusOK)
	}
	return w.w.Write(d)
}
func (w *ProxyResponseWriter) WriteHeader(status int) {
	fmt.Printf("%08x > %d\n", w.n, status)
	w.StatusCode = status
	w.w.WriteHeader(status)
}

type loggingHandler struct {
	h http.Handler
	n uint32
}

func (h *loggingHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	n := atomic.AddUint32(&h.n, 1)
	fmt.Printf("%08x %s %s\n", n, r.Method, r.RequestURI)
	pw := &ProxyResponseWriter{w: w, n: n}
	defer func() {
		if err := recover(); err != nil {
			fmt.Printf("%08x panic %v\n", pw.n, err)
		}
		if pw.StatusCode == 0 {
			http.Error(pw, "", http.StatusInternalServerError)
		}
	}()
	h.h.ServeHTTP(pw, r)
}

func Log(h http.Handler) http.Handler {
	return &loggingHandler{h: h}
}
