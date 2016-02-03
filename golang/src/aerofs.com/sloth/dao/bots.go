package dao

import (
	"aerofs.com/sloth/errors"
	. "aerofs.com/sloth/structs"
	"aerofs.com/sloth/util"
	"database/sql"
)

func GetAllBots(tx *sql.Tx) []Bot {
	botList := make([]Bot, 0)
	rows, err := tx.Query("SELECT id, name, convo_id FROM bots")
	errors.PanicOnErr(err)
	for rows.Next() {
		var bot Bot
		err := rows.Scan(&bot.Id, &bot.Name, &bot.ConvoId)
		errors.PanicOnErr(err)
		botList = append(botList, bot)
	}
	return botList
}

func GetBot(tx *sql.Tx, bid string) *Bot {
	bot := &Bot{Id: bid}
	err := tx.QueryRow("SELECT name, convo_id FROM bots WHERE id=?", bid).Scan(&bot.Name, &bot.ConvoId)
	if err == sql.ErrNoRows {
		return nil
	}
	errors.PanicAndRollbackOnErr(err, tx)
	return bot
}

func NewBot(tx *sql.Tx, name, cid string) *Bot {
	bot := &Bot{
		Id:      util.GenerateRandomId(),
		Name:    name,
		ConvoId: cid,
	}
	_, err := tx.Exec("INSERT INTO bots (id, name, convo_id) VALUES (?,?,?)",
		bot.Id,
		bot.Name,
		bot.ConvoId,
	)
	errors.PanicAndRollbackOnErr(err, tx)
	return bot
}
