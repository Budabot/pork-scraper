(ns com.jkbff.budabot.core
	(:require [clojure.core.async :refer [chan close! <!! >!!]]
			  [clojure.tools.logging :as log]
			  [com.jkbff.budabot.thread :as thread]
			  [com.jkbff.budabot.helper :as helper]
			  [com.jkbff.budabot.pork :as pork]
			  [com.jkbff.budabot.db :as db]
			  [com.jkbff.budabot.metrics :as metrics]
			  [clojure.java.jdbc :as j]
			  [com.jkbff.budabot.config :as config])
	(:import (java.util.concurrent TimeUnit)
			 (com.codahale.metrics ConsoleReporter)))


(def channel-buffer-size 50)
(def thread-pool-factor (config/NUM_THREADS))
(def timeout-in-seconds 10000)

(def compare-fields [:char_id :first_name :last_name :guild_rank_name :level :faction :profession :gender :breed
					 :defender_rank :guild_id :guild_name :deleted])

(defn ^ConsoleReporter report-metrics-to-console
	[metric-registry]
	(->
		(ConsoleReporter/forRegistry metric-registry)
		(.convertRatesTo TimeUnit/SECONDS)
		(.convertDurationsTo TimeUnit/MILLISECONDS)
		.build))

(defn normalize-org-member
	[org-info member]
	{:nickname (helper/trim-string (:name member))
	 :char_id (:char_instance member)
	 :first_name (helper/trim-string (:firstname member))
	 :last_name (helper/trim-string (:lastname member))
	 :guild_rank (:rank member)
	 :guild_rank_name (helper/trim-string (:rank_title member))
	 :level (:levelx member)
	 :faction (helper/trim-string (:side_name org-info))
	 :profession (helper/trim-string (:prof member))
	 :profession_title (helper/trim-string (:prof_title member))
	 :gender (helper/trim-string (:sex member))
	 :breed (helper/trim-string (:breed member))
	 :defender_rank (:alienlevel member)
	 :defender_rank_name (helper/trim-string (:defender_rank_title member))
	 :guild_id (:org_instance org-info)
	 :guild_name (:name org-info)
	 :server (:char_dimension member)
	 :deleted 0})

(defn normalize-single-member
	[org-info char]
	(if (= 1 (:deleted char))
		char

		{:nickname (helper/trim-string (:name char))
		 :char_id (:char_instance char)
		 :first_name (helper/trim-string (:firstname char))
		 :last_name (helper/trim-string (:lastname char))
		 :guild_rank (or (:rank org-info) 0)
		 :guild_rank_name (helper/trim-string (:rank_title org-info))
		 :level (:levelx char)
		 :faction (helper/trim-string (:side char))
		 :profession (helper/trim-string (:prof char))
		 :profession_title (helper/trim-string (:profname char))
		 :gender (helper/trim-string (:sex char))
		 :breed (helper/trim-string (:breed char))
		 :defender_rank (:alienlevel char)
		 :defender_rank_name (helper/trim-string (:rank_name char))
		 :guild_id (or (:org_instance org-info) 0)
		 :guild_name (or (:name org-info) "")
		 :server (:char_dimension char)
		 :deleted 0}))

(defn normalize-guild
	[org-info]
	{:guild_id (:org_instance org-info)
	 :guild_name (:name org-info)
	 :faction (:side_name org-info)
	 :server (:org_dimension org-info)
	 :deleted 0})

(defn update-char-if-changed
	[db-conn db-char char timestamp]
	(let [name (:nickname char)
		  server (:server char)
		  db-char (or db-char (db/get-char db-conn name server))
		  current-char (assoc char :last_checked timestamp :last_changed timestamp)]

		(cond
			; if db-char is nil, insert new record
			(nil? db-char)
			(do
				(db/insert-char db-conn current-char)
				(.inc metrics/inserted-chars-counter))

			; if both are deleted, or both are the same, update last_checked only
			(or (= 1 (:deleted current-char) (:deleted db-char))
				(helper/compare-maps db-char current-char compare-fields))
			(db/update-last-checked db-conn name server timestamp)

			; if current is deleted, mark as deleted in db
			(= 1 (:deleted current-char))
			(do
				(db/delete-char db-conn name server timestamp)
				(.inc metrics/deleted-chars-counter))

			; otherwise update existing record
			:else
			(do
				(db/update-char db-conn current-char)
				(.inc metrics/updated-chars-counter)))))

(defn update-guild-if-changed
	[db-conn org-info timestamp]
	(let [guild-id (:guild_id org-info)
				server (:server org-info)
				db-org-info (db/get-guild db-conn guild-id server)
				current-org-info (assoc org-info :last_checked timestamp :last_changed timestamp)]

		(cond
			; if db-char is nil, insert new record
			(nil? db-org-info)
			(do
				(db/insert-guild db-conn current-org-info)
				;(.inc metrics/inserted-chars-counter)
				)

			; if both are deleted, or both are the same, update last_checked only
			(or (= 1 (:deleted current-org-info) (:deleted db-org-info))
					(helper/compare-maps db-org-info current-org-info [:guild_name :faction :deleted]))
			(db/update-last-checked-guild db-conn guild-id server timestamp)

			; if current is deleted, mark as deleted in db
			(= 1 (:deleted current-org-info))
			(do
				(db/delete-guild db-conn guild-id server timestamp)
				;(.inc metrics/deleted-chars-counter)
				)

			; otherwise update existing record
			:else
			(do
				(db/update-guild db-conn current-org-info)
				;(.inc metrics/updated-chars-counter)
				))))

(defn load-pages
	[orgs-chan dimensions letters]
	(doseq [dimension dimensions
			letter letters]
		(try
			(let [page (pork/get-listing-page letter dimension)]
				(doseq [output (pork/parse-orgs-from-page page)]
					(>!! orgs-chan output)
					(.inc metrics/orgs-chan-counter)))
			(catch Exception e (do (log/error e)
								   (.inc metrics/errors-counter))))))

(defn load-org-details
	[orgs-chan orgs-detail-chan]
	(loop [org-info (<!! orgs-chan)]
		(if-let [[dimension id name] org-info]
			(do
				(.dec metrics/orgs-chan-counter)
				(try
					(let [output (pork/get-org-details id dimension)]
						; check for deleted org?
						(>!! orgs-detail-chan output)
						(.inc metrics/orgs-detail-chan-counter))
					(catch Exception e (do (log/error e "")
										   (.inc metrics/errors-counter))))
				(recur (<!! orgs-chan))))))

(defn save-orgs-to-database
	[orgs-chan timestamp]
	(loop [result (<!! orgs-chan)]
		(if result
			(let [[org-info members last-updated] result
				  members (map #(normalize-org-member org-info %) members)]
				(.dec metrics/orgs-detail-chan-counter)
				(.mark metrics/org-meter)
				;(log/debug last-updated)
				(j/with-db-connection [db-conn (db/get-db)]
					(update-guild-if-changed db-conn (normalize-guild org-info) timestamp)
					(doseq [member members]
						(update-char-if-changed db-conn nil member timestamp)
						(.mark metrics/org-char-meter)))
				(recur (<!! orgs-chan))))))

(defn get-single-chars
	[chars-chan servers timestamp]
	(doseq [server servers
			char (db/get-unchecked-chars timestamp server 0)]
		(>!! chars-chan char)))

(defn load-single-chars
	[chars-chan char-details-chan]
	(loop [db-char-info (<!! chars-chan)]
		(if db-char-info
			(do
				(try
					(if-let [[char-info org-info last-update] (pork/get-char-details (:nickname db-char-info) (:server db-char-info))]
							(>!! char-details-chan [db-char-info (normalize-single-member org-info char-info)])
							(>!! char-details-chan [db-char-info {:nickname (:nickname db-char-info) :server (:server db-char-info) :deleted 1}]))
					(catch Exception e (do (log/error e "")
										   (.inc metrics/errors-counter))))
				(recur (<!! chars-chan))))))

(defn save-single-chars
	[char-details-chan timestamp]
	(j/with-db-connection [db-conn (db/get-db)]
		(loop [result (<!! char-details-chan)]
			(if-let [[db-char-info char-info] result]
				(do
					(update-char-if-changed db-conn db-char-info char-info timestamp)
					(.mark metrics/unorged-char-meter)
					(recur (<!! char-details-chan)))))))

(defn run
	[timestamp]

	; update players on org rosters
	(let [orgs-chan (chan channel-buffer-size)
		  org-details-chan (chan channel-buffer-size)

		  load-pages-pool (thread/execute-in-pool 1 #(load-pages orgs-chan (config/SERVERS) (config/LETTERS)))
		  load-orgs-pool (thread/execute-in-pool (* thread-pool-factor 1)  #(load-org-details orgs-chan org-details-chan))
		  save-orgs-pool (thread/execute-in-pool (* thread-pool-factor 2) #(save-orgs-to-database org-details-chan timestamp))
		  ]

		; TODO if adding to channel fails, end task

		(.awaitTermination load-pages-pool timeout-in-seconds TimeUnit/SECONDS)
		(close! orgs-chan)

		(.awaitTermination load-orgs-pool timeout-in-seconds TimeUnit/SECONDS)
		(close! org-details-chan)

		(.awaitTermination save-orgs-pool timeout-in-seconds TimeUnit/SECONDS))

	; update players not on org rosters
	(let [chars-chan (chan channel-buffer-size)
		  char-details-chan (chan channel-buffer-size)

		  get-chars-pool (thread/execute-in-pool 1 #(get-single-chars chars-chan (config/SERVERS) timestamp))
		  load-chars-pool (thread/execute-in-pool (* thread-pool-factor 2) #(load-single-chars chars-chan char-details-chan))
		  save-chars-pool (thread/execute-in-pool (* thread-pool-factor 2) #(save-single-chars char-details-chan timestamp))]

		(.awaitTermination get-chars-pool timeout-in-seconds TimeUnit/SECONDS)
		(close! chars-chan)

		(.awaitTermination load-chars-pool timeout-in-seconds TimeUnit/SECONDS)
		(close! char-details-chan)

		(.awaitTermination save-chars-pool timeout-in-seconds TimeUnit/SECONDS))

	; update player_history and guild_history tables
	(db/update-player-history timestamp)
	(db/update-guild-history timestamp)
	; guilds not updated should be marked as deleted
	)

(defn -main
	[& args]
	(try
		(log/info "Starting")

		(if (config/CREATE_TABLES)
			(db/create-tables))

		(let [timestamp (helper/unix-epoch-seconds)
			  reporter (report-metrics-to-console metrics/metric-registry)
			  console-reporter-interval (config/CONSOLE_REPORTER_INTERVAL)]

			(if (> console-reporter-interval 0)
				(.start reporter console-reporter-interval TimeUnit/SECONDS))

			(log/info "Batch process" timestamp)
			(db/add-batch-record timestamp)
			(run timestamp)
			(let [elapsed (- (helper/unix-epoch-seconds) timestamp)
				  num-updated (+ (.getCount metrics/inserted-chars-counter)
								 (.getCount metrics/updated-chars-counter)
								 (.getCount metrics/deleted-chars-counter))
				  num-errors (.getCount metrics/errors-counter)]
				(db/update-batch-record timestamp elapsed 1 num-updated num-errors)
				(.report reporter)
				(log/info (str "Elapsed time: " elapsed " secs"))

				(clj-http.conn-mgr/shutdown-manager pork/cm)

				; wait for report to log results
				(Thread/sleep 5000)))

		(log/info "Finished")
		(catch Exception e (.printStackTrace e))))
