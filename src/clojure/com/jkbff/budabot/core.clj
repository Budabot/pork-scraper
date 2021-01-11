(ns com.jkbff.budabot.core
	(:require [clojure.core.async :refer [chan close! <!! >!!]]
			  [clj-http.client :as client]
			  [clojure.data.json :as json]
			  [clojure.tools.logging :as log]
			  [com.jkbff.budabot.thread :as thread]
			  [com.jkbff.budabot.helper :as helper])
	(:import (java.util.concurrent TimeUnit ExecutorService)))


;(def letters ["a" "b" "c" "d" "e" "f" "g" "h" "i" "j" "k" "l" "m" "n" "o" "p" "q" "r" "s" "t" "u" "v" "w" "x" "y" "z"])
(def letters ["a"])
(def channel-buffer-size 100)
(def thread-pool-factor 1)
(def timeout-in-seconds 10000)

(defn request-url
	[url]
	(log/debug "requesting" url)
	(:body (client/get url)))

(defn read-json
	[s]
	(json/read-str s :key-fn #(helper/format-keys (clojure.string/lower-case %))))

(defn get-listing-page
	[letter dimension]
	(request-url (format "http://people.anarchy-online.com/people/lookup/orgs.html?l=%s&dim=%s" letter dimension)))

(defn parse-orgs-from-page
	[page]
	(map #(rest (update-in % [3] clojure.string/trim))
		 (re-seq #"(?s)<a href=\"//people.anarchy-online.com/org/stats/d/(\d+)/name/(\d+)\">(.+?)</a>" page)))

(defn get-org-details
	[org-id dimension]
	(-> (format "http://people.anarchy-online.com/org/stats/d/%s/name/%s/basicstats.xml?data_type=json" dimension org-id)
		request-url
		read-json))

(defn load-pages
	[pages-chan]
	(doseq [dimension ["5" "6"]
			letter letters]
			(>!! pages-chan (get-listing-page letter dimension))))

(defn load-org-details
	[pages-chan orgs-chan]
	(loop [page (<!! pages-chan)]
		(if (not (nil? page))
			(do
				(doseq [output (map (fn [[dimension id name]] (get-org-details id dimension)) (parse-orgs-from-page page))]
					(>!! orgs-chan output))
				(recur (<!! pages-chan))))))

(defn save-orgs-to-database
	[orgs-chan]
	(loop [result (<!! orgs-chan)]
		(if result
			(do
				;(log/info (str "retrieving result " result))
				(log/info (get result 2))
				(Thread/sleep 500)
				;(log/info "saved to db")
				(recur (<!! orgs-chan))))))

(defn run
	[]

	(let [pages-chan (chan 3)
		  orgs-chan (chan channel-buffer-size)

		  load-pages-pool (thread/execute-in-pool (* thread-pool-factor 1) #(load-pages pages-chan))
		  load-orgs-pool (thread/execute-in-pool (* thread-pool-factor 6)  #(load-org-details pages-chan orgs-chan))
		  save-db-pool (thread/execute-in-pool (* thread-pool-factor 3) #(save-orgs-to-database orgs-chan))]

		(.awaitTermination load-pages-pool timeout-in-seconds TimeUnit/SECONDS)
		(close! pages-chan)

		(.awaitTermination load-orgs-pool timeout-in-seconds TimeUnit/SECONDS)
		(close! orgs-chan)

		(.awaitTermination save-db-pool timeout-in-seconds TimeUnit/SECONDS)))

(defn -main
	[& args]
	(log/info "Starting")

	(log/info (helper/timer (run)))

	(log/info "Finished"))
