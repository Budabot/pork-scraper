(ns com.jkbff.budabot.helper
	(:require [clojure.string :as str])
	(:import (java.text SimpleDateFormat)
			 (java.io FilterInputStream)))

(def date-format-pattern "yyyy-MM-dd")
(def date-formatter (SimpleDateFormat. date-format-pattern))

(defn unix-epoch-seconds
	[]
	(quot (System/currentTimeMillis) 1000))

(defn entities-fn
	[e]
	(.replace e \- \_))

(defn identifiers-fn
	[e]
	(.replace e \_ \-))

;; taken from: https://nukelight.wordpress.com/2012/02/29/clojure-one-liners/
(defn format-keys [s]
	(as-> s x
		  (str/split x #"(?=[A-Z])")
		  (str/join "-" x)
		  (str/lower-case x)
		  (str/replace x #"_" "-")
		  (keyword x)))
