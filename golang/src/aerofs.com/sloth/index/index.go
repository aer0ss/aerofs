package index

import (
	"aerofs.com/sloth/errors"
	. "aerofs.com/sloth/structs"
	"encoding/json"
	"github.com/blevesearch/bleve"
	"log"
	"strconv"
)

type Index struct {
	Index            bleve.Index
	InitDoneFilename string
	IndexedFilename  string
}


type KeyType int
const (
	MESSAGE KeyType = iota
	CONVO
	USER
)

type QueryType int
const (
	DEFAULT QueryType = iota
	MATCH
	PREFIX
	FUZZY
)

type ResultID struct {
	Id   string
	Type KeyType
}

// Searches the index for the query string, returning list of IDs sorted by relevance.  Executes queries
// in the following order, merging results at each step and stopping once the limit is reached:
// MatchPhraseQuery, MatchQuery, PrefixQuery, FuzzyQuery.
// TODO: consider performing one search type per request
func (idx *Index) Search(query string, limit int, pageFrom int, pageType QueryType) ([]ResultID,
		int, QueryType) {
	log.Printf("Search: %v, %v\n", query, limit)

	if pageType < DEFAULT || pageType > FUZZY {
		log.Panicf("invalid page type: %v\n", pageType)
	}

	var ids = map[string]bool{}
	var results = []ResultID{}
	var nextPageFrom int
	var nextPageType QueryType

	var searchResults *bleve.SearchResult
	if (pageType == DEFAULT) {
		searchResults, nextPageFrom = idx.search(bleve.NewMatchPhraseQuery(query), pageFrom)
		nextPageType = DEFAULT
		mergeResults(searchResults, &results, &ids)
	}
	if (len(results) < limit && pageType <= MATCH) {
		searchResults, nextPageFrom = idx.search(bleve.NewMatchQuery(query), pageFrom)
		nextPageType = MATCH
		mergeResults(searchResults, &results, &ids)
	}
	if (len(results) < limit && pageType <= PREFIX) {
		searchResults, nextPageFrom = idx.search(bleve.NewPrefixQuery(query), pageFrom)
		nextPageType = PREFIX
		mergeResults(searchResults, &results, &ids)
	}
	if (len(results) < limit) {
		searchResults, nextPageFrom = idx.search(bleve.NewFuzzyQuery(query), pageFrom)
		nextPageType = FUZZY
		mergeResults(searchResults, &results, &ids)
	}
	return results, nextPageFrom + pageFrom, nextPageType
}

func (idx *Index) search(query bleve.Query, from int) (*bleve.SearchResult, int) {
	searchResults, err := (*idx).Index.Search(
		bleve.NewSearchRequestOptions(query, 100, from, false))
	errors.PanicOnErr(err)
	log.Printf("Found %v results\n", searchResults.Hits.Len())
	return searchResults, searchResults.Hits.Len()
}

// Adds messages to the index
// big TODO: free users should only be able to search the most recent 10k messages.  So, for free users
// we'll need to remove N messages from the index in this batch once the 10k limit is hit - that part
// should be doable by simple subtraction since objects are deleted by Id, and message Ids are
// consecutive in the messages table - i.e. if we add message ids 10001 and 10002, we also need to
// delete message ids 1 and 2 from the index.  We will also need an upgrade path to index all old
// messages when free users become paid users.  Alternatively, if we need a quicker fix we can have the
// search endpoint filter messages that are too old.
func (idx *Index) BatchIndexMessages(messages []Message, lastId int64) int64 {
	log.Printf("Update Index with %v messages\n", len(messages))
	batch := (*idx).Index.NewBatch()
	for i := 0; i < len(messages); i++ {
		lastId = messages[i].Id
		id, err := json.Marshal(
			ResultID{
				Id: strconv.FormatInt(messages[i].Id, 10),
				Type: MESSAGE,
			})
		errors.PanicOnErr(err)

		err = batch.Index(string(id), []string{messages[i].Body})
		errors.PanicOnErr(err)
	}
	err := (*idx).Index.Batch(batch)
	errors.PanicOnErr(err)
	return lastId
}

// Adds a convo to the index
func (idx *Index) IndexNewConvo(convo Convo) {
	log.Printf("Update Index with 1 new convo\n")
	if (!convo.IsPublic) {
		return
	}
	err := (*idx).Index.Index(indexKey(convo.Id, CONVO), convoValue(convo))
	errors.PanicOnErr(err)
}

// Updates the index with the new convo info
func (idx *Index) IndexExistingConvo(convo Convo) {
	log.Printf("Update Index with 1 existing convo\n")
	key := indexKey(convo.Id, CONVO)

	err := (*idx).Index.Delete(key)
	errors.PanicOnErr(err)
	if (!convo.IsPublic) {
		return
	}
	err = (*idx).Index.Index(key, convoValue(convo))
	errors.PanicOnErr(err)
}

// Adds a user to the index
func (idx *Index) IndexNewUser(user User) {
	log.Printf("Update Index with 1 new user\n")
	(*idx).Index.Index(indexKey(user.Id, USER), userValue(user))
}

// Updates the index with the new user info
func (idx *Index) IndexExistingUser(user User) {
	log.Printf("Update Index with 1 existing user\n")
	key := indexKey(user.Id, USER)
	err := (*idx).Index.Delete(key)
	errors.PanicOnErr(err)
	err = (*idx).Index.Index(key, userValue(user))
	errors.PanicOnErr(err)
}

// merges and deduplicates the results of the multi-search into the list of ResultIDs
func mergeResults(searchResults *bleve.SearchResult, results *[]ResultID,
		ids *map[string]bool) {
	hits := searchResults.Hits.Len()
	for i := 0; i < hits; i++ {
		result := new(ResultID)
		bytes := []byte(searchResults.Hits[i].ID)
		err := json.Unmarshal(bytes, result)

		errors.PanicOnErr(err)

		if _, containsKey := (*ids)[(*result).Id]; !containsKey {
			*results = append(*results, *result)
			(*ids)[(*result).Id] = true;
		}
	}
}

func indexKey(id string, keyType KeyType) string {
	resultId, err := json.Marshal(ResultID{ Id: id, Type: keyType })
	errors.PanicOnErr(err)
	log.Printf("indexKey: %v", string(resultId))
	return string(resultId)
}

func userValue(user User) []string {
	return []string{user.Id, user.TagId, user.FirstName, user.LastName}
}

func convoValue(convo Convo) []string {
	return []string{convo.Name}
}