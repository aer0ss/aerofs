package auth

type TokenNotFoundError struct {
	Token string
}

func (e TokenNotFoundError) Error() string {
	return "token not found: " + e.Token
}
