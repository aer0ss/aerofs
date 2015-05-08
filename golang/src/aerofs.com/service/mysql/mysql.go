package mysql

import (
	"bytes"
	"database/sql"
	"fmt"
	"io/ioutil"
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

func CreateConnection(url, database string) *sql.DB {
	db, err := sql.Open("mysql", url)
	if err != nil {
		panic(err)
	}
	_, err = db.Exec("CREATE DATABASE IF NOT EXISTS " + database)
	if err != nil {
		panic(err)
	}
	_, err = db.Exec("use " + database)
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
		_, err := tx.Exec("CREATE TABLE IF NOT EXISTS schema_migrations(name VARCHAR(255), PRIMARY KEY(name))")
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
	for i, f := range migrations {
		name := f.Name()
		if len(applied) > i {
			if applied[i] != name {
				panic(fmt.Errorf("mismatched migrations %i %s %s", i, applied[i], f))
			}
			continue
		}
		d, err := ioutil.ReadFile("migration/" + name)
		if err != nil {
			panic(err)
		}
		err = applyMigration(db, name, d)
		if err != nil {
			panic(err)
		}
	}
}

func applyMigration(db *sql.DB, name string, m []byte) error {
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
