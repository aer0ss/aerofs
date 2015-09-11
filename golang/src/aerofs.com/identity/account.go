package main

import (
	"aerofs.com/identity/constants"
	"aerofs.com/service/mysql"
	"crypto/sha256"
	"database/sql"
	"encoding/base64"
	"errors"
	"github.com/emicklei/go-restful"
	"golang.org/x/crypto/scrypt"
	"io/ioutil"
	"log"
	"net/http"
	"strconv"
	"strings"
)

// FIXME: Can you re-activate a deactivated account? Or do you replace it? Currently you can do neither; an update to
// a deleted account is an error.

// Account describes the current state of a given account.
type Account struct {
	FirstName          string `json:"first_name,omitempty" description:"User's first name"`
	LastName           string `json:"last_name,omitempty" description:"User's last name"`
	IsAdmin            bool   `json:"is_admin" description:"Account privilege level"`
	HasLocalCredential bool   `json:"has_local_credential" description:"If true, this account has a local stored credential"`
	IsActive           bool   `json:"is_active" description:"Current account status"`
	HasTwoFactor       bool   `json:"has_two_factor" description:"If true, two-factor auth is required for this user"`
}

// AccountWritable is the data model for update requests from clients; only values that can be changed are allowed.
type AccountWritable struct {
	FirstName *string `json:"first_name,omitempty" description:"User's first name"`
	LastName  *string `json:"last_name,omitempty" description:"User's last name"`
	IsAdmin   *bool   `json:"is_admin,omitempty" description:"Account privilege level"`
}

type AccountsResource struct {
	db *sql.DB
}

func (self *AccountsResource) buildEndpoint() *restful.WebService {
	ws := new(restful.WebService)

	ws.Path("/accounts").
		Doc("Create and verify user accounts").
		Consumes(restful.MIME_JSON, restful.MIME_XML).
		Produces(restful.MIME_JSON, restful.MIME_XML)

	ws.Route(ws.GET("/{userid}").To(self.getAccount).
		Doc("Examine account details and state.").
		Notes("Return the current state and details of the given account, if it exists.").
		Param(ws.PathParameter("userid", "User id (email address), urlencoded").DataType("string")).
		Returns(200, "Account exists and is valid.", Account{}).
		Returns(404, "Account does not exist.", nil))

	ws.Route(ws.PUT("/{userid}").To(self.updateAccount).
		Doc("Create or update an account.").
		Notes("If the account does not exist, create a new account record in the system of record.\n"+
		"\n"+
		"Account details that are provided in the request body will be applied to the backing store.\n"+
		"\n"+
		"This endpoint supports the merge-patch described in RFC 7386(https://tools.ietf.org/html/rfc7386). "+
		"In short, a PUT request may include a subset of the Account object; those fields that are provided will "+
		"replace existing values. To delete field contents in the backing store, include the field in the request "+
		"and set it to null. Fields not included will not be altered.").
		Reads(AccountWritable{}).
		Param(ws.PathParameter("userid", "User id (email address), urlencoded").DataType("string")).
		Returns(200, "Account create or update was successful", Account{}).
		Returns(404, "Account could not be created or updated", nil))

	ws.Route(ws.PUT("/{userid}/credential").To(self.storeCredential).
		Consumes("text/plain").
		Doc("Replace a stored credential.").
		Notes("The credential is expected in clear text; it will be salted and hashed before storing in the backend.").
		Param(ws.BodyParameter("credential", "plaintext credential").DataType("string")).
		Param(ws.PathParameter("userid", "User id (email address), urlencoded").DataType("string")).
		Do(returns204))

	ws.Route(ws.DELETE("/{userid}").To(self.delAccount).
		Doc("Delete an account.").
		Param(ws.PathParameter("userid", "User id (email address), urlencoded").DataType("string")).
		Do(returns204))

	ws.Route(ws.POST("/{userid}/session").To(noop).
		Doc("Create a new session with default lifetime.").
		Notes("Create a session for the given user. Session will be created with the default lifetime").
		Param(ws.PathParameter("userid", "User id (email address), urlencoded").DataType("string")).
		Returns(201, "Path to newly-created session object", ""))

	return ws
}

// updateAccount handles both creation of a new account and updating selected fields of an existing account.
// Unfortunately this means some by-hand string-mangling to build an "on empty update" clause; tread carefully.
// Note that the PUT request follows a merge/patch semantic, meaning fields may be populated, empty, or null;
// null fields in the input record will not be updated in the database.
func (self AccountsResource) updateAccount(req *restful.Request, resp *restful.Response) {
	var (
		firstName string
		lastName  string
		authLevel int
		uid       = req.PathParameter("userid")
		body      = AccountWritable{}
		acct      = Account{}

		updateClauses []string = nil
	)
	log.Printf("updateAccount(%s)\n", uid)

	if err := req.ReadEntity(&body); err != nil {
		resp.WriteError(http.StatusBadRequest, err)
		return
	}

	if body.IsAdmin == nil {
		// default auth level is "user" for new accounts
		authLevel = constants.AuthLevel_User
	} else {
		if *(body.IsAdmin) {
			authLevel = constants.AuthLevel_Admin
		} else {
			authLevel = constants.AuthLevel_User
		}
		updateClauses = append(updateClauses, "u_auth_level="+strconv.Itoa(authLevel))
	}

	if body.FirstName == nil {
		firstName = ""
	} else {
		firstName = *(body.FirstName)
		updateClauses = append(updateClauses, "u_first_name='"+firstName+"'")
	}

	if body.LastName == nil {
		lastName = ""
	} else {
		lastName = *(body.LastName)
		updateClauses = append(updateClauses, "u_last_name='"+lastName+"'")
	}

	if updateClauses == nil {
		resp.WriteError(400, errors.New("Empty update clause in request"))
		return
	}

	err := mysql.Transact(self.db, func(tx *sql.Tx) error {
		_, err := tx.Exec(
			"INSERT INTO sp_user "+
				"SET "+
				"u_id=?, u_hashed_passwd=?, u_first_name=?, u_last_name=?, u_auth_level=?, "+
				"u_org_id=?, u_acl_epoch=?, u_deactivated=?, u_whitelisted=? "+
				"ON DUPLICATE KEY UPDATE "+strings.Join(updateClauses, ","),
			uid,
			make([]byte, 0), // make it impossible to sign in with a credential
			firstName, lastName, authLevel,
			constants.PrivateOrgId,
			constants.InitialAclEpoch,
			false, false)
		if err != nil {
			return err
		}
		return self.getAccountRecord(tx, uid, &acct)
	})

	if err != nil {
		log.Print("Err ", err)
		resp.WriteError(http.StatusNotFound, errors.New("Did not find matching account record"))
	} else {
		resp.WriteEntity(acct)
	}
}

func (self *AccountsResource) getAccount(req *restful.Request, resp *restful.Response) {
	var (
		userid = req.PathParameter("userid")
		acct   = Account{}
	)
	log.Printf("getAccount(%s)\n", userid)

	err := mysql.Transact(self.db, func(tx *sql.Tx) error {
		return self.getAccountRecord(tx, userid, &acct)
	})

	if err == nil {
		resp.WriteEntity(acct)
	} else {
		log.Print("Err ", err)
		resp.WriteError(http.StatusNotFound, errors.New("Did not find matching account record"))
	}
}

func (self *AccountsResource) delAccount(req *restful.Request, resp *restful.Response) {
	var (
		userid             = req.PathParameter("userid")
		rowsAffected int64 = 0
	)
	log.Printf("delAccount(%s)\n", userid)

	err := mysql.Transact(self.db, func(tx *sql.Tx) error {
		rows, err := tx.Exec(
			"UPDATE sp_user "+
				"SET u_deactivated=1 "+
				"WHERE u_id = ?", userid)

		if err == nil {
			rowsAffected, err = rows.RowsAffected()
		}
		return err
	})

	if err != nil || rowsAffected == 0 {
		log.Print("Err ", err)
		resp.WriteError(http.StatusNotFound, errors.New("Failed to disable account"))
	} else {
		resp.WriteHeader(http.StatusNoContent)
	}
}

func (self *AccountsResource) storeCredential(req *restful.Request, resp *restful.Response) {
	var (
		userid             = req.PathParameter("userid")
		rowsAffected int64 = 0
	)
	log.Printf("storeCredential(%s)\n", userid)

	cleartext, err := ioutil.ReadAll(req.Request.Body)
	if err != nil {
		resp.WriteError(400, errors.New("Failed reading request body"))
	}

	shaedCred := base64.StdEncoding.EncodeToString(
		buildShaedScryptedValue(userid, cleartext))

	err = mysql.Transact(self.db, func(tx *sql.Tx) error {
		rows, err := tx.Exec(
			"UPDATE sp_user "+
				"SET u_hashed_passwd=? "+
				"WHERE "+
				"u_id = ? AND u_deactivated=0",
			shaedCred,
			userid)

		if err == nil {
			rowsAffected, err = rows.RowsAffected()
		}
		return err
	})

	if err != nil || rowsAffected == 0 {
		log.Print("Err ", err)
		resp.WriteError(http.StatusNotFound, errors.New("Cannot update credential"))
	} else {
		resp.WriteHeader(http.StatusNoContent)
	}
}

// getAccountRecord retrieves the account record from the db and marshalls it into an Account object.
// IMPORTANT: This expects to be called within a db transaction!!
func (self *AccountsResource) getAccountRecord(tx *sql.Tx, userid string, acct *Account) error {
	var (
		authLevel     int
		isDeactivated bool
		passwdLength  int
		hasTwoFactor  bool
	)

	err := tx.QueryRow(
		"SELECT u_first_name, u_last_name, u_auth_level, u_deactivated, char_length(u_hashed_passwd), u_two_factor_enforced "+
			"FROM sp_user "+
			"WHERE u_id = ? AND u_deactivated=0", userid).
		Scan(&acct.FirstName, &acct.LastName, &authLevel, &isDeactivated, &passwdLength, &hasTwoFactor)

	if err == nil {
		acct.IsAdmin = authLevel == constants.AuthLevel_Admin
		acct.HasLocalCredential = passwdLength == constants.Cred_HashLength
		acct.HasTwoFactor = hasTwoFactor
		acct.IsActive = !isDeactivated
	}
	return err
}

const (
	KD_N      int = 8192
	KD_R      int = 8
	KD_P      int = 1
	KD_KeyLen int = 64
)

var SHA_Passwd_Salt = []byte{
	0x59, 0xeb, 0x04, 0xb5,
	0xb7, 0x32, 0x8c, 0xc9,
	0x92, 0xcd, 0xe4, 0xad,
	0x8c, 0x95, 0x53, 0xc9,
	0x3a, 0x2e, 0x46, 0x36,
	0xf8, 0x65, 0x2e, 0x4e,
	0x57, 0x3b, 0x44, 0x11,
	0x13, 0xc0, 0x16, 0xbc,
	0xec, 0xc9, 0xde, 0x61,
	0x7b, 0x68, 0xbc, 0x8a,
	0x8d, 0x1c, 0x23, 0x67,
	0x96, 0x14, 0x97, 0xdd,
	0x94, 0x31, 0x41, 0x4d,
	0x52, 0xa5, 0x05, 0x23,
	0xa5, 0xb6, 0xc9, 0xb1,
	0x00, 0xe1, 0xef, 0x20}

// Given a user and a cleartext credential value, derive a secure key that we can
// feel good about storing in a production database. The key-generation must be
// repeatable for the given inputs.
//
// Today this means SCrypt with some well-chosen arguments, and the userId functioning as salt.
//
// The value we actually store for a local credential is the SHA-256 of (the scrypt'ed password) plus (a constant salt).
//
func buildShaedScryptedValue(userid string, cleartext []byte) []byte {
	scrypted, err := scrypt.Key(cleartext, []byte(userid), KD_N, KD_R, KD_P, KD_KeyLen)
	if err != nil {
		log.Print("Error scrypting key", err)
		return []byte{0}
	}

	digest := sha256.New()
	digest.Write(scrypted)
	digest.Write(SHA_Passwd_Salt)
	return digest.Sum(nil)
}

func returns204(b *restful.RouteBuilder) {
	b.Returns(http.StatusNoContent, "Account update was successful.", nil)
	b.Returns(http.StatusNotFound, "Account does not exist.", nil)
}
