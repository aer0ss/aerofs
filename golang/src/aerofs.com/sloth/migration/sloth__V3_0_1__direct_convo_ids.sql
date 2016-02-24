UPDATE convos SET id=CONCAT("0", RIGHT(id, 31)) WHERE type=1;
