package auth

type TokenNotFoundError struct {
	Token string
}

func (e TokenNotFoundError) Error() string {
	return "Token not found: " + e.Token
}
