package push

import (
	"aerofs.com/sloth/filters"
	"aerofs.com/sloth/push"
	"github.com/emicklei/go-restful"
	"log"
)

type RegistrationRequest struct {
	Token string
	Dev   bool
}

type context struct {
	pushNotifier push.Notifier
}

func BuildRoutes(checkUser restful.FilterFunction, updateLastOnline restful.FilterFunction, pushNotifier push.Notifier) *restful.WebService {
	ws := new(restful.WebService)
	ws.Filter(checkUser)
	ws.Filter(updateLastOnline)
	ws.Filter(filters.LogRequest)

	ctx := &context{pushNotifier: pushNotifier}

	ws.Path("/push/registry").Doc("Register an iOS device")

	ws.Route(ws.POST("ios").To(ctx.register("ios")).
		Doc("Register an iOS device").
		Consumes(restful.MIME_JSON).
		Reads(RegistrationRequest{}).
		Returns(200, "Success", nil).
		Returns(401, "Invalid authorization", nil))

	ws.Route(ws.POST("android").To(ctx.register("android")).
		Doc("Register an Android device").
		Consumes(restful.MIME_JSON).
		Returns(200, "Success", nil).
		Returns(401, "Invalid authorization", nil))

	return ws
}

func (ctx *context) register(deviceType string) func(*restful.Request, *restful.Response) {
	return func(request *restful.Request, response *restful.Response) {
		caller := request.Attribute(filters.AUTHORIZED_USER).(string)
		params := new(RegistrationRequest)
		err := request.ReadEntity(params)
		if err != nil || params.Token == "" {
			response.WriteHeader(400)
			return
		}

		statusCode := ctx.pushNotifier.Register(deviceType, caller, params.Token, params.Dev)

		log.Printf("Button registration -> %v\n", statusCode)
		if statusCode < 200 || statusCode > 299 {
			response.WriteHeader(502)
		}
	}
}
