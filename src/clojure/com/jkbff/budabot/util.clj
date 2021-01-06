(ns com.jkbff.budabot.util
    (:require [clojure.string :as str]))

;; taken from: https://nukelight.wordpress.com/2012/02/29/clojure-one-liners/
(defn format-keys [s]
    (as-> s x
          (str/split x #"(?=[A-Z])")
          (str/join "-" x)
          (str/lower-case x)
          (str/replace x #"_" "-")
          (keyword x)))