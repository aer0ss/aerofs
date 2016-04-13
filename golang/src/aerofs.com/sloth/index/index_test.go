package index

import (
	"aerofs.com/sloth/structs"
	"github.com/blevesearch/bleve"
	"log"
	"os"
	"testing"
	"time"
)

func TestMain(m *testing.M) {
	// remove timestamp and redirect to stdout for build script compat
	log.SetFlags(0)
	log.SetOutput(os.Stdout)
	log.Println("Test start time:", time.Now())

	//run tests
	os.Exit(m.Run())
}

func TestSearch(t *testing.T) {
	defer os.RemoveAll("test.index")

	mapping := bleve.NewIndexMapping()
	bleve, err := bleve.New("test.index", mapping)
	if err != nil {
		t.Fatal(err)
	}

	idx := &Index{Index: bleve}

	defer bleve.Close()

	var bodies = [...] string{
		"random saying blah blah blah",
		"hey what's going on?",
		"aerofs. cool.",
	}
	var froms = [...] string{
		"foo@aerofs.com",
		"bar@aerofs.com",
		"baz@qux.com",
	}
	var convos = [...] string{
		"asdfasdffoo",
		"asdfasdfbar",
		"asdfasdfbaz",
	}

	var messages = []structs.Message{}
	// create and index 99 messages.
	for i := 1; i <= 99; i++ {
		message := structs.Message{Id: int64(i),
			Body: bodies[i % len(bodies)],
			From: froms[i % len(froms)],
			IsData:false,
			Time:time.Now(),
			To: convos[i % len(convos)],
		}
		messages = append(messages, message)
	}
	lastId := idx.BatchIndexMessages(messages, 1)

	if (lastId < 99) {
		t.Errorf("Expected last indexed id to be 99. Got: %v", lastId)
	}

	expect(t, idx, "body", 0)
	expect(t, idx, "hey", 33)
	expect(t, idx, "random", 33)
	expect(t, idx, "cool.", 33)
	expect(t, idx, "aerofs", 33)
	expect(t, idx, "asdfasdffoo", 0)
	expect(t, idx, "hey random and cool", 99)
	expect(t, idx, "foo@ae", 0)
	expect(t, idx, "uiwertuiqetruiterwuiyte", 0)
}

func expect(t *testing.T, idx *Index, query string, expected int) {
	results, _, _ := idx.Search(query, 100, 0, 0)

	if (len(results) != expected) {
		t.Errorf("Expected # of results for %v to be %v. Got: %v", query, expected, len(results))
	}
}