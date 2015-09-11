package main

import (
	"database/sql"
	"fmt"
	"github.com/emicklei/go-restful"
	"net/http"
)

type Session struct {
	created    string `json:"create_date"`
	expiryTime int64  `json:"expiry_time" description:"UNIX epoch time at which this session expires"`
	provenance string `json:"provenance" description:"Indicates the type of identification used to create the session."`
	isAdmin    bool   `json:"is_admin" description:"True for admin-authorized sessions only."`
}

type SessionsResource struct {
	// DAO goes here
	db *sql.DB
}

func (_ *SessionsResource) buildRoutes() *restful.WebService {
	ws := new(restful.WebService)
	ws.Path("/sessions").
		Doc("Create and verify authenticated sessions.").
		Consumes(restful.MIME_JSON, restful.MIME_XML).
		Produces(restful.MIME_JSON, restful.MIME_XML)

	ws.Route(ws.GET("/{sessionid}").To(noop).
		Doc("Get session details.").
		Notes("Return the current state and details of the given session, if it exists.").
		Param(ws.PathParameter("sessionid", "Opaque session id").DataType("string")).
		Do(sessionReturns200))

	ws.Route(ws.HEAD("/{sessionid}").To(noop).
		Doc("Get session details.").
		Notes("Return the current state and details of the given session, if it exists.").
		Param(ws.PathParameter("sessionid", "Opaque session id").DataType("string")).
		Do(sessionReturns200))

	ws.Route(ws.DELETE("/{sessionid}").To(noop).
		Doc("Invalidate the given session.").
		Notes("Mark the given session as invalid immediately.").
		Param(ws.PathParameter("sessionid", "Opaque session id").DataType("string")).
		Do(sessionReturns204))

	return ws
}

func sessionReturns200(b *restful.RouteBuilder) {
	b.Returns(http.StatusOK, "Session exists and is valid.", nil)
	b.Returns(http.StatusNotFound, "Session does not exist.", nil)
}

func sessionReturns204(b *restful.RouteBuilder) {
	b.Returns(http.StatusNoContent, "Session update was successful.", nil)
	b.Returns(http.StatusNotFound, "Session does not exist.", nil)
}

func noop(req *restful.Request, resp *restful.Response) {
	fmt.Println("Not implemented yet!", req.SelectedRoutePath())
	resp.WriteHeader(204)
}
