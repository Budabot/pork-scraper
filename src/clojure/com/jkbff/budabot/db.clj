(ns com.jkbff.budabot.db
	(:require [com.jkbff.budabot.config :as config]
			  [com.jkbff.budabot.helper :as helper]
			  [clojure.java.jdbc :as j]
			  [clojure.tools.logging :as log]))


(defn get-db
	[]
	{:dbtype   (config/DATABASE_TYPE)
	 :host     (config/DATABASE_HOST)
	 :port     (config/DATABASE_PORT)
	 :user     (config/DATABASE_USERNAME)
	 :password (config/DATABASE_PASSWORD)
	 :dbname   (config/DATABASE_NAME)})

(defn extract-single-result
	[result]
	(case (count result)
		0 nil
		1 (first result)
		(throw (Exception. "More than one result when 0 or 1 results expected"))))

(defn require-single-result
	[result]
	(case (count result)
		1 (first result)
		(throw (Exception. "More than one result or no results when 1 result expected"))))

(defn require-single-update
	[result]
	(case (first result)
		1 result
		(throw (Exception. (str "Expected 1 row updated, actual: " (first result))))))

(defn create-tables
	[]
	(let [sql (slurp "resources/pork_history.sql")
		  statements (clojure.string/split sql #";")]
		(j/db-do-commands (get-db) statements)))

;(defn insert-record
;	[record]
;	(j/insert! (get-db)
;			   :auno_history
;			   record))
;
;(defn insert-records
;	[records]
;	(j/insert-multi! (get-db)
;			   :auno_history
;			   records))

(defn add-batch-record
	[timestamp]
	(j/insert! (get-db) :batch_history
			   {:dt timestamp :elapsed 0 :success 0 :updates 0 :errors 0}))

(defn update-batch-record
	[timestamp elapsed success updates errors]
	(->
		(j/update! (get-db) :batch_history
				   {:elapsed elapsed :success success :updates updates :errors errors}
				   ["dt = ?", timestamp])
		require-single-update))

(defn update-player-history
	[timestamp]
	(j/execute! (get-db) ["INSERT INTO player_history SELECT * FROM player WHERE last_changed = ?"
						  timestamp]))

(defn update-guild-history
	[timestamp]
	(j/execute! (get-db) ["INSERT INTO guild_history SELECT * FROM guild WHERE last_changed = ?"
						  timestamp]))

; player

(defn get-char
	[db-conn name server]
	(->
		(j/query db-conn ["SELECT * FROM player WHERE nickname = ? AND server = ?"
						  name server])
		extract-single-result))

(defn insert-char
	[db-conn char]
	(try
		(j/insert! db-conn :player char)
		(catch Exception e (log/error e char))))

(defn update-char
	[db-conn char]
	(try
		(->
			(j/update! db-conn :player
					   (dissoc char :nickname :server)
					   ["nickname = ? AND server = ?" (:nickname char) (:server char)])
			require-single-update)
		(catch Exception e (log/error e char))))

(defn delete-char
	[db-conn name server timestamp]
	(try
		(->
			(j/update! db-conn :player
					   {:last_checked timestamp :last_changed timestamp :deleted 1}
					   ["nickname = ? AND server = ?" name server])
			require-single-update)
		(catch Exception e (log/error e name server timestamp))))

(defn update-last-checked
	[db-conn name server timestamp]
	(try
		(->
			(j/update! db-conn :player
					   {:last_checked timestamp}
					   ["nickname = ? AND server = ?" name server])
			require-single-update)
		(catch Exception e (log/error e name server timestamp))))

(defn get-unchecked-chars
	[timestamp server deleted]
	(j/query (get-db) ["SELECT * FROM player WHERE last_checked != ? AND server = ? AND deleted = ?"
					   timestamp server deleted]))

;guild

(defn get-guild
	[db-conn guild-id server]
	(try
		(->
			(j/query db-conn ["SELECT * FROM guild WHERE guild_id = ? AND server = ?"
							  guild-id server])
			extract-single-result)
		(catch Exception e (log/error e guild-id server))))

(defn insert-guild
	[db-conn org-info]
	(try
		(j/insert! db-conn :guild org-info)
		(catch Exception e (log/error e org-info))))

(defn update-last-checked-guild
	[db-conn guild-id server timestamp]
	(try
		(->
			(j/update! db-conn :guild
					   {:last_checked timestamp}
					   ["guild_id = ? AND server = ?" guild-id server])
			require-single-update)
		(catch Exception e (log/error e guild-id server timestamp))))

(defn delete-guild
	[db-conn guild-id server timestamp]
	(try
		(->
			(j/update! db-conn :guild
					   {:last_checked timestamp :last_changed timestamp :deleted 1}
					   ["guild_id = ? AND server = ?" guild-id server])
			require-single-update)
		(catch Exception e (log/error e guild-id server timestamp))))

(defn update-guild
	[db-conn org-info]
	(try
		(->
			(j/update! db-conn :guild
					   (dissoc org-info :guild_id :server)
					   ["guild_id = ? AND server = ?" (:guild_id org-info) (:server org-info)])
			require-single-update)
		(catch Exception e (log/error e org-info))))