(ns com.jkbff.budabot.metrics
	(:import (com.codahale.metrics MetricRegistry Counter)))

(def metric-registry (MetricRegistry.))
(def org-meter (.meter metric-registry "orgs"))
(def org-char-meter (.meter metric-registry "org-characters"))
(def unorged-char-meter (.meter metric-registry "unorged-characters"))
(def inserted-chars-counter (.counter metric-registry "inserted"))
(def updated-chars-counter (.counter metric-registry "updated"))
(def deleted-chars-counter (.counter metric-registry "deleted"))
(def retry-counter (.counter metric-registry "retry"))
(def ^Counter errors-counter (.counter metric-registry "errors"))

(def pages-chan-counter (.counter metric-registry "pages-chan-items"))
(def orgs-chan-counter (.counter metric-registry "orgs-chan-items"))