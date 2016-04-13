package search

import (
	"aerofs.com/sloth/index"
	"aerofs.com/sloth/lastOnline"
	. "aerofs.com/sloth/structs"
	"database/sql"
	"github.com/blevesearch/bleve"
	"log"
	"os"
	"strconv"
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

// TODO: add several more test cases.  So far this isn't much more than a copy of index_test.go
func TestSearch(t *testing.T) {
	defer os.RemoveAll("test.index")

	mapping := bleve.NewIndexMapping()
	bleve, err := bleve.New("test.index", mapping)
	if err != nil {
		t.Fatal(err)
	}
	defer bleve.Close()

	idx := &index.Index{Index: bleve}

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

	var messages = make([]Message, 0)
	// create and index 99 messages.
	for i := 1; i <= 99; i++ {
		message := Message{Id: int64(i),
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

	expect(t, idx, "hey", 33)
	expect(t, idx, "random", 33)
	expect(t, idx, "cool.", 33)
	expect(t, idx, "aerofs", 33)
	expect(t, idx, "asdfasdffoo", 0)
	expect(t, idx, "hey random and cool", 40)
	expect(t, idx, "foo@ae", 0)
}

func expect(t *testing.T, idx *index.Index, query string, expected int) {
	var fetcher stubFetcher
	results := searchIndex(idx, fetcher, nil, nil, query, 40, 0, 0, "foo@bar.baz")
	if (results.Next != nil) {
		log.Printf(*(results.Next))
	}
	if (expected == 40 && results.Next == nil) {
		t.Errorf("Expected non-nil Next link")
	}
	if (len(results.Results) != expected) {
		t.Errorf("Expected # of results for %v to be %v. Got: %v", query, expected,
			len(results.Results))
	}
}

type stubFetcher struct{}

func (s stubFetcher) FetchObjects(ids []index.ResultID, db *sql.DB, lastOnlineTimes *lastOnline.Times,
		caller string, limit int) ([]Result, int) {
	results := []Result{}
	for i := 0; i < len(ids) && len(results) < limit; i++ {
		switch ids[i].Type {
		case index.MESSAGE:
			mid, _ := strconv.ParseInt(ids[i].Id, 10, 64)
			results = append(results, Result{Message: &Message{Id:mid}})
		case index.CONVO:
			results = append(results, Result{Convo: &Convo{Id:ids[i].Id}})
		case index.USER:
			results = append(results, Result{User: &User{Id:ids[i].Id}})
		default:
			panic("unrecognized result")
		}
	}
	return results, len(results)
}