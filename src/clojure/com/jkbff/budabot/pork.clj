(ns com.jkbff.budabot.pork
	(:require [clojure.tools.logging :as log]
			  [com.jkbff.budabot.helper :as helper]
			  [clojure.data.json :as json]
			  [clj-http.client :as client]))

(defn request-url
	[url]
	(log/debug "requesting" url)
	(try
		(:body (client/get url {:socket-timeout 10000
								:connection-timeout 10000
								:retry-handler (fn [ex try-count http-context]
												   (log/debug (str "attempt " try-count " for url '" url "'"))
												   (Thread/sleep 1000)
												   (if (> try-count 4) false true))}))
		(catch Exception e (do (log/error e (str "error while retrieving url '" url "'"))))))

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

(defn get-char-details
	[name dimension]
	(-> (format "http://people.anarchy-online.com/character/bio/d/%d/name/%s/bio.xml?data_type=json" dimension name)
		request-url
		read-json))