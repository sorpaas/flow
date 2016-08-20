(ns flow.core
  (:require [mount.core :refer [defstate] :as mount]

            [flow.server :refer [server]]
            [flow.db :refer [db]]
            [flow.websocket :refer [channel-socket]])
  (:gen-class))

(defn -main [& args]
  (mount/start))
