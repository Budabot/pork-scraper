(ns com.jkbff.budabot.process-xml
	(:require [com.jkbff.budabot.config :as config]
			  [com.jkbff.budabot.db :as db]
			  [clojure.xml :as xml])
	(:import (java.io File FileInputStream)
			 (com.jkbff.budabot FilterUnicodeInputStream)))


(defn traverse-xml
	[x & args]
	(loop [elem x
		   k (first args)
		   keys (rest args)]
		(if (nil? k)
			elem
			(recur (first (filter #(= k (:tag %)) (:content elem)))
				   (first keys)
				   (rest keys)))))

(defn elem-to-str
	[elem]
	(first (:content elem)))

(defn get-dimension
	[^File f]
	(let [file-name (.getName f)]
		(cond
			(clojure.string/ends-with? file-name ".1.xml") 1
			(clojure.string/ends-with? file-name ".2.xml") 2
			:else (throw (Exception. (str "Unknown dimension for file name " file-name))))))

(defn create-user-record
	[elem dimension]
	(let [name (traverse-xml elem :name)
		  basic-stats (traverse-xml elem :basic_stats)
		  ;org (traverse-xml elem :organization_membership)
		  ]

		{:first_name (elem-to-str (traverse-xml name :firstname))
		 :name (elem-to-str (traverse-xml name :nick))
		 :last_name (elem-to-str (traverse-xml name :lastname))

		 ;:level (elem-to-str (traverse-xml basic-stats :level))
		 :breed (elem-to-str (traverse-xml basic-stats :breed))
		 :gender (elem-to-str (traverse-xml basic-stats :gender))
		 ;:faction (elem-to-str (traverse-xml basic-stats :faction))
		 :profession (elem-to-str (traverse-xml basic-stats :profession))
		 ;:defender-rank (elem-to-str (traverse-xml basic-stats :defender_rank_id))
		 ;:defender-rank-name (elem-to-str (traverse-xml basic-stats :defender_rank))

		 ;:org-name (elem-to-str (traverse-xml org :organization_name))
		 ;:org-rank (elem-to-str (traverse-xml org :rank_id))
		 ;:org-rank-name (elem-to-str (traverse-xml org :rank))

		 :dimension dimension

		 ;:last-changed (parse-timestamp (traverse-xml elem :last_changed))
		 ;:last-updated (parse-timestamp (traverse-xml elem :last_updated))
		 }))

(defn empty-str-to
	[str default]
	(if (clojure.string/blank? str)
		default
		str))

(defn get-history-attrs
	[elem]
	(let [attrs (:attrs elem)]
		{:level (:level attrs)
		 :defender_rank (empty-str-to (:ailevel attrs) 0)
		 :faction (:faction attrs)
		 :org_name (:guild attrs)
		 :org_rank_name (:rank attrs)
		 :date (:date attrs)}))

(defn get-history-records
	[elem]
	(let [history-entries (:content (traverse-xml elem :history))]
		(map get-history-attrs history-entries)))

(defn get-records
	[elem dimension]
	(let [user-record (create-user-record elem dimension)
		  history-records (get-history-records elem)]
		(map #(merge user-record %) history-records)))

(defn get-record-as-insert
	[record]
	(str "INSERT INTO auno_history ("
		 "name,"
		 "first_name,"
		 "last_name,"
		 "breed,"
		 "gender,"
		 "profession,"
		 "level,"
		 "defender_rank,"
		 "faction,"
		 "org_name,"
		 "org_rank_name,"
		 "date,"
		 "dimension"
		 ") VALUES ("
		 "'" (:name record) "',"
		 "'" (:first_name record) "',"
		 "'" (:last_name record) "',"
		 "'" (:breed record) "',"
		 "'" (:gender record) "',"
		 "'" (:profession record) "',"
		 (:level record) ","
		 (:defender_rank record) ","
		 "'" (:faction record) "',"
		 "'" (.replace (:org_name record) "'" "''") "',"
		 "'" (:org_rank_name record) "',"
		 "'" (:date record) "',"
		 (:dimension record)
		 ");\n"))

(defn run
	[]
	(let [dir (File. (config/XML_FILES_PATH))]

		(with-open [w (clojure.java.io/writer "output_full.sql" :append false)]

			(doseq [f (.listFiles dir)]
				;(try
					(let [elem (xml/parse (FilterUnicodeInputStream. (FileInputStream. f)))
						  records (get-records elem (get-dimension f))
						  file-name (.getName f)]

						;(println file-name)

						;(db/insert-records records)
						(doseq [record records]
							(.write w (get-record-as-insert record)))
						;(.delete f)
					)
					;(catch Exception e (do
					;					   (println "error for file" (.getName f))
					;					   (.printStackTrace e))))
				))))

(defn -main
	[& args]
	(time (run)))
