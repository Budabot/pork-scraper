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

(defn insert-record
	[record]
	(j/insert! (get-db)
			   :auno_history
			   record))

(defn insert-records
	[records]
	(j/insert-multi! (get-db)
			   :auno_history
			   records))
