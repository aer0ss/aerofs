package main

import (
	"aerofs.com/service/mysql"
	"database/sql"
	"fmt"
	"github.com/emicklei/forest"
	"github.com/hydrogen18/memlistener"
	"github.com/stretchr/testify/assert"
	"net/http"
	"os"
	"testing"
)

// globals? yeah, for brevity in test methods
var db *sql.DB
var server *memlistener.MemoryServer
var client *forest.APITesting
var sampleUser = AccountWritable{strPtr("Firsty"), strPtr("Lasto"), nil}

const testDB = "aerofs_sp_go_test"

// ----
// unit test cases

func TestSimpleGetError(t *testing.T) {
	cfg := forest.NewConfig("/accounts/{userid}", "get404@account").
		Header("Accept", "application/json").
		Header("Content-Type", "application/json")

	forest.ExpectStatus(t, client.GET(t, cfg), 404)
}

func TestSimpleCreate(t *testing.T) {
	cfg := forest.NewConfig("/accounts/{userid}", "create@account").
		Header("Accept", "application/json").
		Header("Content-Type", "application/json").
		Content(sampleUser, "application/json")

	// precondition: does not exist before
	forest.ExpectStatus(t, client.GET(t, cfg), 404)
	// create...
	forest.ExpectStatus(t, client.PUT(t, cfg), 200)
}

func TestSimpleGet(t *testing.T) {
	cfg := forest.NewConfig("/accounts/{userid}", "create@account").
		Header("Accept", "application/json").
		Header("Content-Type", "application/json")

	resp := Account{}

	forest.ExpectStatus(t, client.PUT(t, cfg.Content(sampleUser, "application/json")), 200)
	forest.ExpectJSONDocument(t, client.GET(t, cfg), &resp)
	assert.True(t, resp.IsActive)
	assert.False(t, resp.HasLocalCredential)
	assert.False(t, resp.HasTwoFactor)
	assert.Equal(t, *sampleUser.FirstName, resp.FirstName)
	assert.Equal(t, *sampleUser.LastName, resp.LastName)
}

func TestUpdateMissingBody(t *testing.T) {
	cfg := forest.NewConfig("/accounts/{userid}", "badRequest@account").
		Header("Accept", "application/json").
		Header("Content-Type", "application/json").
		Content(AccountWritable{}, "application/json")

	forest.ExpectStatus(t, client.PUT(t, cfg), http.StatusBadRequest)
}

func TestUpdateAccount(t *testing.T) {
	notAdmin := false
	isAdmin := true
	cfg := forest.NewConfig("/accounts/{userid}", "update@account").
		Header("Accept", "application/json").
		Header("Content-Type", "application/json")

	forest.ExpectStatus(t, client.PUT(t, cfg.Content(AccountWritable{strPtr("F1"), strPtr("L1"), &notAdmin}, "application/json")), 200)
	forest.ExpectStatus(t, client.PUT(t, cfg.Content(AccountWritable{strPtr("F2"), strPtr("L2"), &isAdmin}, "application/json")), 200)

	resp := Account{}
	forest.ExpectJSONDocument(t, client.GET(t, cfg), &resp)
	assert.True(t, resp.IsAdmin)
	assert.Equal(t, "F2", resp.FirstName)
	assert.Equal(t, "L2", resp.LastName)
}

func TestUpdatePartial(t *testing.T) {
	notAdmin := false
	cfg := forest.NewConfig("/accounts/{userid}", "partial@account").
		Header("Accept", "application/json").
		Header("Content-Type", "application/json")

	forest.ExpectStatus(t, client.PUT(t, cfg.Content(AccountWritable{strPtr("F1"), strPtr("L1"), &notAdmin}, "application/json")), 200)
	forest.ExpectStatus(t, client.PUT(t, cfg.Content(AccountWritable{strPtr("F2"), nil, nil}, "application/json")), 200)

	resp := Account{}
	forest.ExpectJSONDocument(t, client.GET(t, cfg), &resp)
	assert.Equal(t, "F2", resp.FirstName)
	assert.Equal(t, "L1", resp.LastName)
	assert.False(t, resp.IsAdmin)
}


func TestCredentialUpdate(t *testing.T) {
	acctCfg := forest.NewConfig("/accounts/{userid}", "simplecred@account").
		Header("Accept", "application/json").
		Header("Content-Type", "application/json")
	credCfg := forest.NewConfig("/accounts/{userid}/credential", "simplecred@account").
		Header("Accept", "application/json").
		Header("Content-Type", "application/json")

	forest.ExpectStatus(t, client.PUT(t, acctCfg.Content(sampleUser, "application/json")), 200)
	assert.False(t, forest.JSONPath(t, client.GET(t, acctCfg), ".has_local_credential") ==  true)

	// update credential
	forest.ExpectStatus(t, client.PUT(t, credCfg.Content("himom", "text/plain")), 204)
	assert.True(t, forest.JSONPath(t, client.GET(t, acctCfg), ".has_local_credential") == true)
}

func TestCredentialUpdateBadAccount(t *testing.T) {
	credCfg := forest.NewConfig("/accounts/{userid}/credential", "does.not.exist@account").
		Header("Accept", "application/json").
		Header("Content-Type", "application/json")

	forest.ExpectStatus(t, client.PUT(t, credCfg.Content("himom", "text/plain")), 404)
}

func TestDelete404(t *testing.T) {
	cfg := forest.NewConfig("/accounts/{userid}", "delete404@account").
		Header("Accept", "application/json").
		Header("Content-Type", "application/json")

	forest.ExpectStatus(t, client.DELETE(t, cfg), http.StatusNotFound)
}

func TestDupeDelete404(t *testing.T) {
	cfg := forest.NewConfig("/accounts/{userid}", "delete404@account").
		Header("Accept", "application/json").
		Header("Content-Type", "application/json")

	forest.ExpectStatus(t, client.PUT(t, cfg.Content(sampleUser, "application/json")), http.StatusOK)
	forest.ExpectStatus(t, client.DELETE(t, cfg), http.StatusNoContent)
	forest.ExpectStatus(t, client.DELETE(t, cfg), http.StatusNotFound)
}

func TestDisabledAccountReturns404(t *testing.T) {
	cfg := forest.NewConfig("/accounts/{userid}", "disabled@account").
		Header("Accept", "application/json").
		Header("Content-Type", "application/json").
		Content(sampleUser, "application/json")

	// precondition: account does not exist
	forest.ExpectStatus(t, client.PUT(t, cfg), http.StatusOK)
	forest.ExpectStatus(t, client.GET(t, cfg), http.StatusOK)
	forest.ExpectStatus(t, client.DELETE(t, cfg), http.StatusNoContent)
	// after delete, account does not exist
	forest.ExpectStatus(t, client.GET(t, cfg), http.StatusNotFound)
}

// Sanity-check the sha/scrypt function
func TestShaedScryptedPass(t *testing.T) {
	val1 := buildShaedScryptedValue("jon@aerofs.com", []byte("himom"))
	val2 := buildShaedScryptedValue("jon@aerofs.com", []byte("Himom"))
	val3 := buildShaedScryptedValue("jon@aerofs.com", []byte("hi mom"))
	val4 := buildShaedScryptedValue("bob@aerofs.com", []byte("himom"))

	assert.Equal(t, 32, len(val1))
	assert.NotEqual(t, val1, val2)
	assert.NotEqual(t, val1, val3)
	assert.NotEqual(t, val1, val4)
	assert.Equal(t, val1, buildShaedScryptedValue("jon@aerofs.com", []byte("himom")))
}


// ----
// helper funcs

// initializeDatabase will create a connection, drop aerofs_sp_test - then
// re-create the connection to cause database migration to run.
func initializeDatabase() {
	// FIXME: more graceful way to build mysql DSN for unit testing?
	db = mysql.CreateConnection("root@tcp(localhost:3306)/", testDB)
	fmt.Println("Drop database %s ...", testDB)
	db.Exec("drop database " + testDB)

	fmt.Println("Apply database migration in %s ...", testDB)
	db = mysql.CreateConnection("root@tcp(localhost:3306)/", testDB)

	// Trivial database setup. FIXME: why?
	db.Exec("insert into sp_organization SET o_id=2, o_name='main org'")
}

func strPtr(s string) *string {
	return &s
}

// TestMain sets up the database and http server (with an in-memory replacement for the network stack) and stores
// them in evil, evil, global space. Deliciously evil.
func TestMain(m *testing.M) {
	initializeDatabase()

	server = memlistener.NewInMemoryServer(
		CreateServer(db, "testserver", 0, "").Handler)
	client = forest.NewClient("http://testserver", server.NewClient())

	// run all tests...
	retval := m.Run()

	server.Listener.Close()
	os.Exit(retval)
}
