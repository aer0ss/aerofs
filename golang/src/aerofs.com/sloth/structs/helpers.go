package structs

func (c *Convo) HasMember(uid string) bool {
	for _, val := range c.Members {
		if val == uid {
			return true
		}
	}
	return false
}

func (c *Convo) AddMember(uid string) {
	if c.Members == nil {
		c.Members = make([]string, 0)
	}
	// ensure idempotency
	for _, val := range c.Members {
		if val == uid {
			return
		}
	}
	c.Members = append(c.Members, uid)
}
