(ns com.jkbff.budabot.config)

(defn get-env-string
	[name]
	(System/getenv name))

(defn get-env-int
	[name]
	(Integer/parseInt (get-env-string name)))

(defn DATABASE_TYPE [] (get-env-string "DATABASE_TYPE"))
(defn DATABASE_NAME [] (get-env-string "DATABASE_NAME"))
(defn DATABASE_HOST [] (get-env-string "DATABASE_HOST"))
(defn DATABASE_USERNAME [] (get-env-string "DATABASE_USERNAME"))
(defn DATABASE_PASSWORD [] (get-env-string "DATABASE_PASSWORD"))

(defn XML_FILES_PATH [] (get-env-string "XML_FILES_PATH"))