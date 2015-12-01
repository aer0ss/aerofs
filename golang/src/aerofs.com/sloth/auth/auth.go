package auth

//
// Public interface
//

type TokenVerifier interface {
	// Verify the owner of an auth token
	//
	// On success, returns the UID of the token owner
	// On error, the UID is "" and the error is not nil
	VerifyToken(token string) (string, error)
}

func NewTokenVerifier() TokenVerifier {
	return new(echoTokenVerifier)
}

//
// Echo Implementation
//

type echoTokenVerifier struct{}

func (tv *echoTokenVerifier) VerifyToken(token string) (string, error) {
	return token, nil
}
