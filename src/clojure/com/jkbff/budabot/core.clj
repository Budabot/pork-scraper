(ns com.jkbff.budabot.core
	(:require [clojure.core.async :as async :refer [go go-loop thread chan close! <! <!! >! >!!]]
			  [clj-http.client :as client]
			  [com.jkbff.budabot.thread :as thread])
	(:import (java.util.concurrent TimeUnit)))


;(def letters ["a" "b" "c" "d" "e" "f" "g" "h" "i" "j" "k" "l" "m" "n" "o" "p" "q" "r" "s" "t" "u" "v" "w" "x" "y" "z"])
(def letters ["a"])
(def channel-buffer-size 100)
(def thread-pool-factor 1)
(def timeout-in-seconds 10000)

(defn log [& args]
	(print (apply str (conj (interpose " " args) "\n")))
	(flush))

(defn request-url
	[url]
	(log "requesting" url)
	(:body (client/get url)))

(defn get-listing-page
	[letter dimension]
	(request-url (format "http://people.anarchy-online.com/people/lookup/orgs.html?l=%s&dim=%s" letter dimension)))

(defn parse-orgs-from-page
	[page]
	(map #(rest (update-in % [3] clojure.string/trim))
		 (re-seq #"(?s)<a href=\"//people.anarchy-online.com/org/stats/d/(\d+)/name/(\d+)\">(.+?)</a>" page)))

(defn get-org-details [org-id dimension]
	(request-url (format "http://people.anarchy-online.com/org/stats/d/%s/name/%s/basicstats.xml?data_type=json" dimension org-id)))

(defn load-pages
	[url-chan]
	(doseq [dimension ["5" "6"]
			letter letters]
			(>!! url-chan (get-listing-page letter dimension)))
	(close! url-chan))

(defn load-org-details
	[page]
	(map (fn [[dimension id name]] (get-org-details id dimension)) (parse-orgs-from-page page)))

(defn run
	[]

	(let [pages-chan (chan 3)
		  orgs-chan (chan channel-buffer-size)

		  load-pages-pool (thread/execute-in-pool (* thread-pool-factor 1) #(load-pages pages-chan))
		  load-orgs-pool (thread/process-channel (* thread-pool-factor 9) pages-chan orgs-chan load-org-details)]

		(loop [result (<!! orgs-chan)]
			(if result
				(do
					;(println (str "retrieving result " result))
					(Thread/sleep 500)
					(log "received result")
					(recur (<!! orgs-chan))
					)))

		(doseq [pool [load-pages-pool load-orgs-pool]]
			(.awaitTermination pool timeout-in-seconds TimeUnit/MILLISECONDS))

		))

(defn -main
	[& args]
	(println "Starting")

	(time (run))

	(println "Finished"))
