package structs

func (g *Group) HasMember(uid string) bool {
	for _, val := range g.Members {
		if val == uid {
			return true
		}
	}
	return false
}

func (g *Group) AddMember(uid string) {
	if g.Members == nil {
		g.Members = make([]string, 0)
	}
	// ensure idempotency
	for _, val := range g.Members {
		if val == uid {
			return
		}
	}
	g.Members = append(g.Members, uid)
}
