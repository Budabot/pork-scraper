(ns com.jkbff.budabot.db
	(:require [com.jkbff.budabot.config :as config]
			  [com.jkbff.budabot.helper :as helper]
			  [clojure.java.jdbc :as j]))


(defn get-db
	[]
	{:dbtype   (config/DATABASE_TYPE)
	 :dbname   (config/DATABASE_NAME)
	 :user     (config/DATABASE_USERNAME)
	 :host     (config/DATABASE_HOST)
	 :password (config/DATABASE_PASSWORD)})

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

(defn get-char
	[db-conn name server]
	(->
		(j/query db-conn ["SELECT * FROM player WHERE nickname = ? AND server = ?"
						   name server])
		extract-single-result))

(defn insert-char
	[db-conn char]
	(j/insert! db-conn :player char))

(defn update-char
	[db-conn char]
	(->
		(j/update! db-conn :player (dissoc char :nickname :server)
				   ["nickname = ? AND server = ?" (:nickname char) (:server char)])
		require-single-update))

(defn delete-char
	[db-conn name server timestamp]
	(->
		(j/update! db-conn :player {:last_checked timestamp :last_changed timestamp :deleted 1}
				   ["nickname = ? AND server = ?" name server])
		require-single-update))

(defn update-last-checked
	[db-conn name server timestamp]
	(->
		(j/update! db-conn :player {:last_checked timestamp}
				   ["nickname = ? AND server = ?" name server])
		require-single-update))

(defn add-batch-record
	[timestamp]
	(j/insert! (get-db) :batch_history {:dt timestamp :elapsed 0 :success 0 :updates 0 :errors 0}))

(defn update-batch-record
	[timestamp elapsed success updates errors]
	(->
		(j/update! (get-db) :batch_history {:elapsed elapsed :success success :updates updates :errors errors}
				   ["dt = ?", timestamp])
		require-single-update))

(defn get-unchecked-chars
	[timestamp]
	(j/query (get-db) ["SELECT * FROM player WHERE last_checked != ?" timestamp]))

(defn update-player-history
	[timestamp]
	(j/execute! (get-db) ["INSERT INTO player_history SELECT * FROM player WHERE last_changed = ?"
						  timestamp]))