(ns js.reload
  (:require [mount.core :as mount]))

(defn reload! []
  (mount/stop)
  (mount/start))
