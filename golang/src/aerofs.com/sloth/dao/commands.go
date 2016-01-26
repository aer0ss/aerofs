package dao

import (
	"aerofs.com/sloth/errors"
	. "aerofs.com/sloth/structs"
	"database/sql"
)

// Return if a row exists
func CommandExists(tx *sql.Tx, command string) bool {
	err := tx.QueryRow("SELECT command FROM commands WHERE command=?", command).Scan(new(string))
	if err == sql.ErrNoRows {
		return false
	}
	return true
}

// Retrieve a command
func GetCommand(tx *sql.Tx, command string) (*Command, error) {
	cmd := new(Command)
	err := tx.QueryRow("SELECT * FROM commands WHERE command=?", command).Scan(
		&cmd.Command, &cmd.Method, &cmd.URL, &cmd.Token, &cmd.Syntax, &cmd.Description)
	if err != nil {
		return nil, err
	}
	return cmd, nil
}

// Return a list of all commands
func GetAllCommands(tx *sql.Tx) []Command {
	cmdList := make([]Command, 0)
	rows, err := tx.Query("SELECT command,method,url,token,syntax,description FROM commands")
	defer rows.Close()
	errors.PanicAndRollbackOnErr(err, tx)

	for rows.Next() {
		var c Command
		err := rows.Scan(&c.Command, &c.Method, &c.URL, &c.Token, &c.Syntax, &c.Description)
		errors.PanicOnErr(err)
		cmdList = append(cmdList, c)
	}
	return cmdList
}

// Create a new command
func InsertCommand(tx *sql.Tx, params *Command) error {
	_, err := tx.Exec(`INSERT INTO commands (command,method,url,token,syntax,description)`+
		` VALUES (?,?,?,?,?,?)`,
		params.Command, params.Method, params.URL, params.Token, params.Syntax, params.Description)
	return err
}
