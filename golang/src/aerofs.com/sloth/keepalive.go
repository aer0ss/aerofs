package main

import (
	"github.com/emicklei/go-restful"
)

func BuildKeepaliveRoutes() *restful.WebService {
	ws := new(restful.WebService)
	ws.Filter(CheckUser)
	ws.Filter(UpdateLastOnline)
	ws.Filter(LogRequest)

	ws.Path("/keepalive").Doc("Update last-online time")
	ws.Route(ws.POST("").To(noop).
		Doc("Update last-online time and return 200").
		Consumes(restful.MIME_JSON).
		Produces(restful.MIME_JSON).
		Returns(200, "Success", nil).
		Returns(401, "Invalid authorization", nil))
	return ws
}

func noop(request *restful.Request, response *restful.Response) {}
