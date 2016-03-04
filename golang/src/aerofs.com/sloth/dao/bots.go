package dao

import (
	"aerofs.com/sloth/errors"
	. "aerofs.com/sloth/structs"
	"aerofs.com/sloth/util"
	"database/sql"
	"time"
)

const BOT_QUERY_COLUMNS = "id, name, convo_id, ISNULL(avatar), creator_id, creation_time"

// Retrieve a list of all bot users
func GetAllBots(tx *sql.Tx) []Bot {
	botList := make([]Bot, 0)
	rows, err := tx.Query("SELECT " + BOT_QUERY_COLUMNS + " FROM bots")
	errors.PanicOnErr(err)
	for rows.Next() {
		var bot Bot
		var hasNoAvatar bool
		var creationTime int64
		err := rows.Scan(&bot.Id, &bot.Name, &bot.ConvoId, &hasNoAvatar, &bot.CreatorId,
			&creationTime)
		errors.PanicOnErr(err)

		if !hasNoAvatar {
			bot.AvatarPath = "/bots/" + bot.Id + "/avatar"
		}
		bot.CreatedTime = time.Unix(0, creationTime)
		botList = append(botList, bot)
	}

	return botList
}

// Retrieve a single bot user
func GetBot(tx *sql.Tx, bid string) *Bot {
	bot := &Bot{Id: bid}
	err := tx.QueryRow("SELECT name, convo_id, creator_id FROM bots WHERE id=?",
		bid).Scan(&bot.Name, &bot.ConvoId, &bot.CreatorId)
	if err == sql.ErrNoRows {
		return nil
	}
	errors.PanicAndRollbackOnErr(err, tx)
	return bot
}

// Create a new bot user
func NewBot(tx *sql.Tx, name, convoId, creatorId string) *Bot {
	bot := &Bot{
		Id:          util.GenerateRandomId(),
		Name:        name,
		ConvoId:     convoId,
		CreatorId:   creatorId,
		CreatedTime: time.Now(),
	}
	_, err := tx.Exec("INSERT INTO bots (id, name, convo_id, creator_id, creation_time) VALUES (?,?,?,?, ?)",
		bot.Id,
		bot.Name,
		bot.ConvoId,
		bot.CreatorId,
		bot.CreatedTime.UnixNano(),
	)
	errors.PanicAndRollbackOnErr(err, tx)
	return bot
}

// Update a bot user's profile
func UpdateBot(tx *sql.Tx, bid, name string) {
	_, err := tx.Exec("UPDATE bots SET name=? WHERE id=?",
		name, bid)
	errors.PanicAndRollbackOnErr(err, tx)
}

// Update a bot user's avatar
func UpdateBotAvatar(tx *sql.Tx, bid string, avatar []byte) {
	_, err := tx.Exec("UPDATE bots SET avatar=? WHERE id=?",
		avatar, bid)
	errors.PanicAndRollbackOnErr(err, tx)
}

// Retrieve a bot user's avatar
func GetBotAvatar(tx *sql.Tx, bid string) []byte {
	var avatar []byte
	err := tx.QueryRow("SELECT avatar FROM bots WHERE id=?", bid).Scan(&avatar)
	if err == sql.ErrNoRows || len(avatar) == 0 {
		return nil
	}
	errors.PanicAndRollbackOnErr(err, tx)
	return avatar
}
