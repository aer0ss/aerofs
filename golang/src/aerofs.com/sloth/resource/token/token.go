package token

import (
	"aerofs.com/sloth/auth"
	"aerofs.com/sloth/errors"
	"github.com/emicklei/go-restful"
	"log"
	"net/http"
)

const BIFROST_TOKEN_URL = "http://sparta.service:8700/token"

type TokenResource struct {
	tokenCache *auth.TokenCache
}

func BuildRoutes(tokenCache *auth.TokenCache) *restful.WebService {
	ws := new(restful.WebService)
	r := TokenResource{tokenCache: tokenCache}

	ws.Path("/token").Doc("Manage Auth Tokens")
	ws.Route(ws.DELETE("/{token}").To(r.deleteToken).
		Doc("Delete Bifrost token").
		Notes("Deletes the token in Bifrost and in the auth cache").
		Returns(200, "Success", nil).
		Returns(404, "Token not found", nil))

	return ws
}

func (r TokenResource) deleteToken(request *restful.Request, response *restful.Response) {
	token := request.PathParameter("token")
	url := BIFROST_TOKEN_URL + "/" + token
	req, err := http.NewRequest("DELETE", url, nil)
	errors.PanicOnErr(err)
	resp, err := new(http.Client).Do(req)
	errors.PanicOnErr(err)
	if resp.StatusCode == 404 {
		response.WriteHeader(404)
	}
	log.Printf("delete token %v -> %v\n", token, resp.StatusCode)
	r.tokenCache.Delete(token)
}
