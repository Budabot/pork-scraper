(ns com.jkbff.budabot.thread
    (:require [clojure.core.async :as async])
    (:import (java.util.concurrent Executors)))

(defn execute-in-pool
    [n func]
    (let [pool (Executors/newFixedThreadPool n)]
        (.execute pool func)
        (.shutdown pool)
        pool))

(defn ^:ExecutorService process-channel
    [n in-chan out-chan func]
    (execute-in-pool n (fn [] (loop [item (async/<!! in-chan)]
                                  (if (not (nil? item))
                                      (do
                                          (doseq [output (func item)]
                                              (async/>!! out-chan output))
                                          (recur (async/<!! in-chan)))))
                           (async/close! out-chan))))
