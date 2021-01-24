DROP TABLE player;
CREATE TABLE player (
	nickname VARCHAR(50) NOT NULL,
	char_id INT,
	first_name VARCHAR(50) NOT NULL,
	last_name VARCHAR(50) NOT NULL,
	guild_rank INT NOT NULL,
	guild_rank_name VARCHAR(50) NOT NULL,
	level INT NOT NULL,
	faction VARCHAR(50) NOT NULL,
	profession VARCHAR(50) NOT NULL,
	profession_title VARCHAR(50) NOT NULL,
	gender VARCHAR(50) NOT NULL,
	breed VARCHAR(50) NOT NULL,
	defender_rank INT NOT NULL,
	defender_rank_name VARCHAR(50) NOT NULL,
	guild_id INT NOT NULL,
	guild_name VARCHAR(255) NOT NULL,
	server INT NOT NULL,
	deleted SMALLINT NOT NULL,
	last_checked INT NOT NULL,
	last_changed INT NOT NULL,
	PRIMARY KEY(nickname, server)
);

CREATE INDEX `player_nickname_server` ON player(`nickname` ASC, `server` ASC);
CREATE INDEX `player_last_checked` ON player(`last_checked` DESC);
CREATE INDEX `player_last_changed` ON player(`last_changed` DESC);

DROP TABLE player_history;
CREATE TABLE player_history (
	nickname VARCHAR(50) NOT NULL,
	char_id INT,
	first_name VARCHAR(50) NOT NULL,
	last_name VARCHAR(50) NOT NULL,
	guild_rank INT NOT NULL,
	guild_rank_name VARCHAR(50) NOT NULL,
	level INT NOT NULL,
	faction VARCHAR(50) NOT NULL,
	profession VARCHAR(50) NOT NULL,
	profession_title VARCHAR(50) NOT NULL,
	gender VARCHAR(50) NOT NULL,
	breed VARCHAR(50) NOT NULL,
	defender_rank INT NOT NULL,
	defender_rank_name VARCHAR(50) NOT NULL,
	guild_id INT NOT NULL,
	guild_name VARCHAR(255) NOT NULL,
	server INT NOT NULL,
	deleted SMALLINT NOT NULL,
	last_checked INT NOT NULL,
	last_changed INT NOT NULL
);

DROP TABLE guild;
CREATE TABLE guild (
	guild_id INT NOT NULL,
	guild_name VARCHAR(255) NOT NULL,
	faction VARCHAR(50) NOT NULL,
	server INT NOT NULL,
	deleted SMALLINT NOT NULL,
	last_checked INT NOT NULL,
	last_changed INT NOT NULL,
	PRIMARY KEY(guild_id, server)
);

DROP TABLE guild_history;
CREATE TABLE guild_history (
	guild_id INT NOT NULL,
	guild_name VARCHAR(255) NOT NULL,
	faction VARCHAR(50) NOT NULL,
	server INT NOT NULL,
	deleted SMALLINT NOT NULL,
	last_checked INT NOT NULL,
	last_changed INT NOT NULL
);

DROP TABLE history_requests;
CREATE TABLE history_requests (
	request VARCHAR(50) NOT NULL,
	server INT NOT NULL,
	dt INT NOT NULL,
	ip VARCHAR(50) NOT NULL
);

DROP TABLE batch_history;
CREATE TABLE batch_history (
	dt INT NOT NULL,
	elapsed INT NOT NULL,
	success SMALLINT NOT NULL,
	updates INT NOT NULL,
	errors INT NOT NULL
);