(ns com.jkbff.budabot.core
	(:require [clojure.core.async :refer [chan close! <!! >!!]]
			  [clojure.tools.logging :as log]
			  [com.jkbff.budabot.thread :as thread]
			  [com.jkbff.budabot.helper :as helper]
			  [com.jkbff.budabot.pork :as pork]
			  [com.jkbff.budabot.db :as db]
			  [clojure.java.jdbc :as j]
			  [com.jkbff.budabot.config :as config])
	(:import (java.util.concurrent TimeUnit)
			 (com.codahale.metrics MetricRegistry ConsoleReporter Counter)))


(def letters (config/LETTERS))
(def dimensions (config/SERVERS))
(def channel-buffer-size 100)
(def thread-pool-factor 1)
(def timeout-in-seconds 10000)

(def compare-fields [:first_name :last_name :guild_rank_name :level :faction :profession :gender :breed
					 :defender_rank :guild_id :guild_name :deleted])

(def metric-registry (MetricRegistry.))
(def org-meter (.meter metric-registry "orgs"))
(def org-char-meter (.meter metric-registry "org-characters"))
(def unchecked-char-meter (.meter metric-registry "unorged-characters"))
(def inserted-chars-counter (.counter metric-registry "inserted"))
(def updated-chars-counter (.counter metric-registry "updated"))
(def deleted-chars-counter (.counter metric-registry "deleted"))
(def ^Counter errors-counter (.counter metric-registry "errors"))

(def pages-chan-counter (.counter metric-registry "pages-chan-items"))
(def orgs-chan-counter (.counter metric-registry "orgs-chan-items"))

(defn load-pages
	[pages-chan]
	(doseq [dimension dimensions
			letter letters]
		(try
			(>!! pages-chan (pork/get-listing-page letter dimension))
			(.inc pages-chan-counter)
			(catch Exception e (do (log/error e)
								   (.inc errors-counter))))))

(defn load-org-details
	[pages-chan orgs-chan]
	(loop [page (<!! pages-chan)]
		(if page
			(do
				(.dec pages-chan-counter)
				(try
					(doseq [output (map (fn [[dimension id name]] (pork/get-org-details id dimension)) (pork/parse-orgs-from-page page))]
						(>!! orgs-chan output)
						(.inc orgs-chan-counter))
					(catch Exception e (do (log/error e "")
										   (.inc errors-counter))))
				(recur (<!! pages-chan))))))

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
	 :char_id (:char-instance member)
	 :first_name (helper/trim-string (:firstname member))
	 :last_name (helper/trim-string (:lastname member))
	 :guild_rank (:rank member)
	 :guild_rank_name (helper/trim-string (:rank-title member))
	 :level (:levelx member)
	 :faction (helper/trim-string (:side-name org-info))
	 :profession (helper/trim-string (:prof member))
	 :profession_title (helper/trim-string (:prof-title member))
	 :gender (helper/trim-string (:sex member))
	 :breed (helper/trim-string (:breed member))
	 :defender_rank (:alienlevel member)
	 :defender_rank_name (helper/trim-string (:defender-rank-title member))
	 :guild_id (:org-instance org-info)
	 :guild_name (:name org-info)
	 :server (:char-dimension member)
	 :deleted 0})

(defn normalize-single-member
	[org-info char]
	(if (= 1 (:deleted char))
		char

		{:nickname (helper/trim-string (:name char))
		 :char_id (:char-instance char)
		 :first_name (helper/trim-string (:firstname char))
		 :last_name (helper/trim-string (:lastname char))
		 :guild_rank (or (:rank org-info) 0)
		 :guild_rank_name (helper/trim-string (:rank-title org-info))
		 :level (:levelx char)
		 :faction (helper/trim-string (:side char))
		 :profession (helper/trim-string (:prof char))
		 :profession_title (helper/trim-string (:profname char))
		 :gender (helper/trim-string (:sex char))
		 :breed (helper/trim-string (:breed char))
		 :defender_rank (:alienlevel char)
		 :defender_rank_name (helper/trim-string (:rank-name char))
		 :guild_id (or (:org-instance org-info) 0)
		 :guild_name (or (:name org-info) "")
		 :server (:char-dimension char)
		 :deleted 0}))

(defn update-char-if-changed
	[db-conn char timestamp]
	(let [name (:nickname char)
		  server (:server char)
		  db-char (db/get-char db-conn name server)
		  current-char (assoc char :last_checked timestamp :last_changed timestamp)]

		(cond
			; if db-char is nil, insert new record
			(nil? db-char)
			(do
				(db/insert-char db-conn current-char)
				(.inc inserted-chars-counter))

			; if both are deleted, or both are the same, update last_checked only
			(or (= 1 (:deleted current-char) (:deleted db-char)) (helper/compare-maps db-char current-char compare-fields))
			(db/update-last-checked db-conn name server timestamp)

			; if current is deleted, mark as deleted in db
			(= 1 (:deleted current-char))
			(do
				(db/delete-char db-conn name server timestamp)
				(.inc deleted-chars-counter))

			; otherwise update existing record
			:else
			(do
				(db/update-char db-conn current-char)
				(.inc updated-chars-counter)))))

(defn save-orgs-to-database
	[orgs-chan timestamp]
	(loop [result (<!! orgs-chan)]
		(if result
			(let [[org-info members last-updated] result
				  members (map #(normalize-org-member org-info %) members)]
				(.dec orgs-chan-counter)
				(.mark org-meter)
				;(log/debug last-updated)
				(j/with-db-connection [db-conn (db/get-db)]
					(doseq [member members]
						(update-char-if-changed db-conn member timestamp)
						(.mark org-char-meter)))
				(recur (<!! orgs-chan))))))

(defn load-single-chars
	[chars-chan timestamp]
	(doseq [char (db/get-unchecked-chars timestamp)]
		(try
			(let [result (pork/get-char-details (:nickname char) (:server char))]
				(if (nil? result)
					(>!! chars-chan [{:nickname (:nickname char) :server (:server char) :deleted 1} nil nil])
					(>!! chars-chan result)))
			(catch Exception e (do (log/error e)
								   (.inc errors-counter))))
		(.mark unchecked-char-meter)))

(defn save-single-chars
	[chars-chan timestamp]
	(j/with-db-connection [db-conn (db/get-db)]
		(loop [result (<!! chars-chan)]
			(if result
				(let [[char org-info last-update] result
					  normalized-char (normalize-single-member org-info char)]
					(update-char-if-changed db-conn normalized-char timestamp)
					(recur (<!! chars-chan)))))))

(defn run
	[timestamp]

	; update players on org rosters
	(let [pages-chan (chan 3)
		  orgs-chan (chan channel-buffer-size)

		  load-pages-pool (thread/execute-in-pool (* thread-pool-factor 1) #(load-pages pages-chan))
		  load-orgs-pool (thread/execute-in-pool (* thread-pool-factor 4)  #(load-org-details pages-chan orgs-chan))
		  save-orgs-pool (thread/execute-in-pool (* thread-pool-factor 2) #(save-orgs-to-database orgs-chan timestamp))
		  ]

		(.awaitTermination load-pages-pool timeout-in-seconds TimeUnit/SECONDS)
		(close! pages-chan)

		(.awaitTermination load-orgs-pool timeout-in-seconds TimeUnit/SECONDS)
		(close! orgs-chan)

		(.awaitTermination save-orgs-pool timeout-in-seconds TimeUnit/SECONDS))

	; update players not on org rosters
	(let [chars-chan (chan channel-buffer-size)

		  load-chars-pool (thread/execute-in-pool (* thread-pool-factor 1) #(load-single-chars chars-chan timestamp))
		  save-chars-pool (thread/execute-in-pool (* thread-pool-factor 9) #(save-single-chars chars-chan timestamp))]

		(.awaitTermination load-chars-pool timeout-in-seconds TimeUnit/SECONDS)
		(close! chars-chan)

		(.awaitTermination save-chars-pool timeout-in-seconds TimeUnit/SECONDS))

	; update player_history table
	(db/update-player-history timestamp))

(defn -main
	[& args]
	(try
		(log/info "Starting")

		(if (config/CREATE_TABLES)
			(db/create-tables))

		(let [timestamp (helper/unix-epoch-seconds)
			  reporter (report-metrics-to-console metric-registry )]
			(.start reporter 300 TimeUnit/SECONDS)
			(log/info "Batch process" timestamp)
			(db/add-batch-record timestamp)
			(run timestamp)
			(let [elapsed (- (helper/unix-epoch-seconds) timestamp)
				  num-updated (+ (.getCount inserted-chars-counter)
								 (.getCount updated-chars-counter)
								 (.getCount deleted-chars-counter))
				  num-errors (.getCount errors-counter)]
				(db/update-batch-record timestamp elapsed 1 num-updated num-errors)
				(.report reporter)
				(log/info (str "Elapsed time: " elapsed " secs"))))

		(log/info "Finished")
		(catch Exception e (.printStackTrace e))))
