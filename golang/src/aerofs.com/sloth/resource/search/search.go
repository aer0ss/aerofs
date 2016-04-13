package search

import (
	"aerofs.com/sloth/dao"
	"aerofs.com/sloth/errors"
	"aerofs.com/sloth/index"
	"aerofs.com/sloth/filters"
	"aerofs.com/sloth/lastOnline"
	. "aerofs.com/sloth/structs"
	"database/sql"
	"fmt"
	"github.com/emicklei/go-restful"
	"log"
	"net/url"
	"strconv"
)

const DEFAULT_PAGE_SIZE int = 25
const MAX_PAGE_SIZE int = 250
const INDEX_LIMIT_RATIO = 10

func BuildRoutes(db *sql.DB,
	idx *index.Index,
	lastOnlineTimes *lastOnline.Times,
	checkUser restful.FilterFunction,
	updateLastOnline restful.FilterFunction,
) *restful.WebService {

	ws := new(restful.WebService)
	ws.Filter(checkUser)
	ws.Filter(updateLastOnline)
	ws.Filter(filters.LogRequest)

	ctx := &context{db: db, idx: idx, lastOnlineTimes: lastOnlineTimes}

	ws.Path("/search").Doc("Search messages and files")

	ws.Route(ws.GET("").To(ctx.search).
		Doc("Searches for messages and files that are visible to the authorized user").
		Produces(restful.MIME_JSON).
		Returns(200, "A list of messages", MessageList{}).
		Returns(401, "Invalid Authorization", nil))

	return ws
}

// Performs a combined search, returning messages, convos, and users.  This allows all results to be
// sorted by relevance, making it obvious which is the "best match" to display on the front end.
// Messages are filtered based on user permissions using a mysql query (by search.go) after the index is
// searched. Executes queries in the following order, merging results at each step and stopping once
// the limit is reached:
// MatchPhraseQuery, MatchQuery, PrefixQuery, FuzzyQuery.

// Pagination: a 'next' field is returned with the search results if more results are available. It
// contains a link to the next page of results. Bleve has built-in offset-based pagination, so it's
// basically a thin wrapper around that.  The main addition is a result of possibly running multiple
// searches (from most specific to least specific) when we haven't hit the result limit.  Hence two
// query params are required rather than one - an offset query param ("from") and a 'search type'
// query param ("type") to pick up the search where it left off.
func (ctx *context) search(request *restful.Request, response *restful.Response) {
	query := request.QueryParameter("query")
	if query == "" {
		response.WriteErrorString(400, "\"query\" param is required")
		return
	}

	limit := DEFAULT_PAGE_SIZE
	limitParam := request.QueryParameter("limit")
	if limitParam != "" {
		var err error
		limit, err = strconv.Atoi(limitParam)
		if err != nil || limit < 1 {
			log.Print(err)
			response.WriteErrorString(400, "\"limit\" param must be a positive integer")
			return
		}
		if (limit > MAX_PAGE_SIZE) {
			limit = MAX_PAGE_SIZE
		}
	}

	from := 0
	fromParam := request.QueryParameter("from")
	if fromParam != "" {
		var err error
		from, err = strconv.Atoi(fromParam)
		if err != nil || from < 0 {
			log.Print(err)
			response.WriteErrorString(400, "\"from\" param must be a non-negative integer")
			return
		}
	}

	queryType := index.DEFAULT
	typeParam := request.QueryParameter("type")
	if typeParam != "" {
		var err error
		queryTypeInt, err := strconv.Atoi(typeParam)
		queryType = index.QueryType(queryTypeInt)
		if err != nil || queryType < index.DEFAULT || queryType > index.FUZZY{
			log.Print(err)
			response.WriteErrorString(400, fmt.Sprint("\"type\" param must between ",
				index.DEFAULT, " and ", index.FUZZY))
			return
		}
	}

	var fetcher objectFetcher
	results := searchIndex((*ctx).idx, fetcher, (*ctx).db, (*ctx).lastOnlineTimes, query, limit,
		from, queryType, request.Attribute(filters.AUTHORIZED_USER).(string))

	response.WriteEntity(results)
}

func searchIndex(idx *index.Index, fetcher ObjectFetcher, db *sql.DB, lastOnlineTimes *lastOnline.Times,
		query string, limit int, pageFrom int, pageType index.QueryType, caller string) ResultsList {
	results := []Result{}
	for len(results) < limit {
		var resultIds []index.ResultID
		resultIds, pageFrom, pageType = idx.Search(query, INDEX_LIMIT_RATIO * limit, pageFrom,
			pageType)

		if (len(resultIds) == 0) {
			break
		}

		partialResults, idx := fetcher.FetchObjects(resultIds, db, lastOnlineTimes, caller,
			limit)
		pageFrom -= (len(resultIds) - idx)
		results = append(results, partialResults...)

		if (len(resultIds) < limit) {
			break
		}
	}
	log.Printf("Search Messages (%v, %v, %v), found: %v results\n", caller, query, limit,
		len(results))
	resultsList := ResultsList{Results:results}

	// TODO: fix json including unicode '\u0026' instead of &
	if (len(results) == limit) {
		next := fmt.Sprintf("/messaging/search?query=%v&limit=%v&from=%v&type=%v",
			url.QueryEscape(query), limit, pageFrom, pageType)
		resultsList.Next = &next
	}

	return resultsList
}

type context struct {
	db              *sql.DB
	idx             *index.Index
	lastOnlineTimes *lastOnline.Times
}

type data struct {
	Name string
	Type string
	ID   string
}

type ResultsList struct {
	Results []Result `json:"results"`
	Next    *string  `json:"next,omitempty"`
}

type Result struct {
	Message *Message `json:"message,omitempty"`
	Convo   *Convo   `json:"convo,omitempty"`
	User    *User    `json:"user,omitempty"`
}

type ObjectFetcher interface {
	FetchObjects(ids []index.ResultID, db *sql.DB, lastOnlineTimes *lastOnline.Times,
		caller string, limit int) ([]Result, int)
}

type objectFetcher struct {}

func (o objectFetcher) FetchObjects(ids []index.ResultID, db *sql.DB, lastOnlineTimes *lastOnline.Times,
		caller string, limit int) ([]Result, int) {
	messageMap := map[int64]Message{}
	mids := []int64{}
	for _, id := range ids {
		if (id.Type == index.MESSAGE) {
			mid, err := strconv.ParseInt(id.Id, 10, 64)
			errors.PanicOnErr(err)
			mids = append(mids, mid)
		}
	}
	if (len(mids) > 0) {
		tx := dao.BeginOrPanic(db)
		messageMap = dao.FilterMessages(tx, caller, mids)
		dao.CommitOrPanic(tx)
	}

	results := []Result{}
	var idx int
	for idx = 0; idx < len(ids) && len(results) < limit; idx++ {
		switch ids[idx].Type {
		case index.MESSAGE:
			mid, err := strconv.ParseInt(ids[idx].Id, 10, 64)
			errors.PanicOnErr(err)
			if message, containsKey := messageMap[mid]; containsKey {
				results = append(results, Result{Message: &message})
			}
		case index.CONVO:
			tx := dao.BeginOrPanic(db)
			convo := *(dao.GetMinimumConvo(tx, ids[idx].Id))
			dao.CommitOrPanic(tx)
			results = append(results, Result{Convo: &convo})
		case index.USER:
			tx := dao.BeginOrPanic(db)
			user := *(dao.GetUser(tx, ids[idx].Id, lastOnlineTimes))
			dao.CommitOrPanic(tx)
			results = append(results, Result{User: &user})
		default:
			panic(fmt.Sprint("unrecognized result: ", ids[idx].Type))
		}
	}
	return results, idx
}