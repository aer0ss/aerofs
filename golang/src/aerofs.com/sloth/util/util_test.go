package util

import (
	"testing"
)

func TestIsTagPresent(t *testing.T) {
	if IsTagPresent("@jgray", "jgray") != true {
		t.Fail()
	}
	if IsTagPresent("hi @jgray", "jgray") != true {
		t.Fail()
	}
	if IsTagPresent("@jgray hi", "jgray") != true {
		t.Fail()
	}
	if IsTagPresent("hi @jgray, what's up", "jgray") != true {
		t.Fail()
	}
	if IsTagPresent("hi @jgray!", "jgray") != true {
		t.Fail()
	}
	if IsTagPresent("@dgray @jgray @ggray hi", "jgray") != true {
		t.Fail()
	}
	if IsTagPresent("jgray", "jgray") != false {
		t.Fail()
	}
	if IsTagPresent("jgray@jgray.com", "jgray") != false {
		t.Fail()
	}
	if IsTagPresent("@dgray", "jgray") != false {
		t.Fail()
	}
	if IsTagPresent("@jgrayyy", "jgray") != false {
		t.Fail()
	}
}
