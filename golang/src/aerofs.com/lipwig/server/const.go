package server

const respEvent = "000 "

var (
	respOk           = []byte("200\n")
	respBadRequest   = []byte("400\n")
	respUnauthorized = []byte("401\n")
	respNotFound     = []byte("404\n")
	respNotAllowed   = []byte("405\n")
)