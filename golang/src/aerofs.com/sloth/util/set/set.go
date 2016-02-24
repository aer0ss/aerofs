package set

type Set map[string]struct{}

func (s Set) Add(k string) {
	s[k] = struct{}{}
}

func (s Set) Remove(k string) {
	delete(s, k)
}

func (s Set) Diff(t Set) Set {
	r := New()
	for k := range s {
		if _, ok := t[k]; !ok {
			r.Add(k)
		}
	}
	return r
}

func New() Set {
	return make(Set)
}

func From(ks []string) Set {
	r := New()
	for _, k := range ks {
		r.Add(k)
	}
	return r
}
