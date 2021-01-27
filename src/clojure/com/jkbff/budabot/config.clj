(ns com.jkbff.budabot.config)

(defn get-env-string
	[name]
	(System/getenv name))

(defn get-env-int
	[name]
	(Integer/parseInt (get-env-string name)))

(defn get-env-bool
	[name]
	(case (.toLowerCase (get-env-string name))
		"false" false
		"true" true))

(defn DATABASE_TYPE [] (get-env-string "DATABASE_TYPE"))
(defn DATABASE_NAME [] (get-env-string "DATABASE_NAME"))
(defn DATABASE_HOST [] (get-env-string "DATABASE_HOST"))
(defn DATABASE_PORT [] (get-env-string "DATABASE_PORT"))
(defn DATABASE_USERNAME [] (get-env-string "DATABASE_USERNAME"))
(defn DATABASE_PASSWORD [] (get-env-string "DATABASE_PASSWORD"))

(defn XML_FILES_PATH [] (get-env-string "XML_FILES_PATH"))

(defn LETTERS [] (clojure.string/split (get-env-string "PORK_SCRAPER_LETTERS") #","))
(defn SERVERS [] (clojure.string/split (get-env-string "PORK_SCRAPER_SERVERS") #","))
(defn CREATE_TABLES [] (get-env-bool "PORK_SCRAPER_CREATE_TABLES"))
(defn NUM_THREADS [] (get-env-int "PORK_SCRAPER_NUM_THREADS"))