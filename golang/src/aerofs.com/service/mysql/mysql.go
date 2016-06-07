package mysql

import (
	"bytes"
	"database/sql"
	"fmt"
	"io/ioutil"
	"strings"
)

import _ "github.com/go-sql-driver/mysql"

func UrlFromConfig(c map[string]string) string {
	url := c["mysql.url"]
	user := c["mysql.user"]
	passwd := c["mysql.password"]
	auth := "root"
	if len(user) > 0 {
		auth = user
	}
	if len(passwd) > 0 {
		auth = auth + ":" + passwd
	}
	return auth + "@tcp(" + url + ")/"
}

func CreateDatabaseIfNeeded(url, database string) {
	db, err := sql.Open("mysql", url)
	if err != nil {
		panic(err)
	}
	defer db.Close()
	_, err = db.Exec("CREATE DATABASE IF NOT EXISTS " + database)
	if err != nil {
		panic(err)
	}
}

/**
 * CreateConnection returns a DB instance using the given mysql url and database name.
 * Migrations from /migration are applied automatically as needed.
 */
func CreateConnection(url, database string) *sql.DB {
	return CreateConnectionWithParams(url, database, "")
}

/**
 * @params: comma-separated connection params, e.g. "charset=utf8mb4"
 */
func CreateConnectionWithParams(url, database, params string) *sql.DB {
	var dsn = url + database
	if params != "" {
		dsn = dsn + "?" + params
	}
	CreateDatabaseIfNeeded(url, database)
	db, err := sql.Open("mysql", dsn)
	if err != nil {
		panic(err)
	}
	migrate(db)
	return db
}

func Transact(db *sql.DB, txFunc func(*sql.Tx) error) (err error) {
	tx, err := db.Begin()
	if err != nil {
		return
	}
	defer func() {
		if p := recover(); p != nil {
			switch p := p.(type) {
			case error:
				err = p
			default:
				err = fmt.Errorf("%s", p)
			}
		}
		if err != nil {
			tx.Rollback()
			return
		}
		err = tx.Commit()
	}()
	return txFunc(tx)
}

func initMigrationsTable(db *sql.DB) {
	err := Transact(db, func(tx *sql.Tx) error {
		_, err := tx.Exec("CREATE TABLE IF NOT EXISTS schema_migrations(name VARCHAR(255) CHARACTER SET latin1, PRIMARY KEY(name))")
		return err
	})
	if err != nil {
		panic(err)
	}
}

func appliedMigrations(db *sql.DB) []string {
	var migrations []string
	err := Transact(db, func(tx *sql.Tx) error {
		rows, err := tx.Query("select name from schema_migrations")
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var name string
			err = rows.Scan(&name)
			if err != nil {
				return err
			}
			migrations = append(migrations, name)
		}
		return rows.Err()
	})
	if err != nil {
		panic(err)
	}
	return migrations
}

// Read sql migrations from 'migration/' dir and apply them sequentially
func migrate(db *sql.DB) {
	initMigrationsTable(db)
	applied := appliedMigrations(db)
	migrations, err := ioutil.ReadDir("migration")
	if err != nil {
		panic(err)
	}
	i := 0
	for _, f := range migrations {
		name := f.Name()
		if !strings.HasSuffix(name, ".sql") {
			fmt.Println("skipping:", name)
			continue
		}
		if len(applied) > i {
			if applied[i] != name {
				panic(fmt.Errorf("mismatched migrations %v %s %s", i, applied[i], name))
			}
			fmt.Println("migration already applied:", name)
		} else {
			d, err := ioutil.ReadFile("migration/" + name)
			if err != nil {
				panic(err)
			}
			fmt.Println("apply migration:", name)
			err = applyMigration(db, name, d)
			if err != nil {
				panic(err)
			}
		}
		i += 1
	}
}

func applyMigration(db *sql.DB, name string, m []byte) error {

	// First we remove comment lines; then we re-split it by semicolons and execute each as a statement.

	m = removeCommentLines(m)
	return Transact(db, func(tx *sql.Tx) error {
		for _, stmt := range bytes.Split(m, []byte{';'}) {
			stmt = bytes.TrimSpace(stmt)
			if len(stmt) > 0 {
				_, err := tx.Exec(string(stmt))
				if err != nil {
					return err
				}
			}
		}
		_, err := tx.Exec("INSERT INTO schema_migrations(name) VALUES(?)", name)
		return err
	})
}

/** Strip lines that begin with '--'; if those lines contain semicolons, the other split will get confused.
 */
func removeCommentLines(m []byte) []byte {
	retval := make([]byte, 0)
	for _, line := range bytes.Split(m, []byte{'\n'}) {
		if len(line) < 2 || line[0] != '-' || line[1] != '-' {
			retval = append(retval, line...)
		}
	}
	return retval
}
