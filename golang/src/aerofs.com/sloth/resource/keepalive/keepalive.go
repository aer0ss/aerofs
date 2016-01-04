package keepalive

import (
	"aerofs.com/sloth/filters"
	"github.com/emicklei/go-restful"
)

func BuildRoutes(checkUser restful.FilterFunction, updateLastOnline restful.FilterFunction) *restful.WebService {
	ws := new(restful.WebService)
	ws.Filter(checkUser)
	ws.Filter(updateLastOnline)
	ws.Filter(filters.LogRequest)

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
