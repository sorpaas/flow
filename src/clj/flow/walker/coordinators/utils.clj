(ns flow.walker.coordinators.utils
  (:require [clojure.core.async :as async :refer (<! <!! >! >!! put! chan go go-loop close! alts! timeout)]
            [clojure.data :refer [diff]]
            [flow.walker.core :refer [Coordinator reset-route! put-outport! put-inport! walker-graph-db
                                      register-watcher! deregister-watcher!
                                      self-watcher-filter]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]

            [flow.docs.graphs :refer [find-node-by-id
                                      find-graph-by-id
                                      find-graph-by-name
                                      find-connection
                                      commit-graph!]]
            [flow.utils :refer [generate-id]]))

(defmacro go-with-watchers
  {:style/indent 2}
  [walker watchers & body]
  `(let ~(into [] (map-indexed (fn [i v] (if (even? i)
                                           v
                                           `(register-watcher! ~walker ~v))) watchers))
     (go
       ~@body
       (doseq [k# (keys ~watchers)]
         (let [[watcher-id# in# out#] k#]
           (close! in#)
           (close! out#)
           (deregister-watcher! ~walker watcher-id#))))))

(defn find-graph-by-route [walker route]
  (let [graph-id (:type (last route))]
    (find-graph-by-id (walker-graph-db walker) graph-id)))

(defn reset-if-changed! [walker route graph]
  (if (not= graph (find-graph-by-id (walker-graph-db walker) (:id graph)))
    (do (reset-route! walker route) true)
    false))

(defn in-params-watcher! [walker route]
  (let [[watcher-id in out] (register-watcher! walker (self-watcher-filter route))
        out-chan (chan)]
    (go
      (loop [in-params {}]
        (let [[val port] (alts! [in out])]
          (when-let [[route sender-id port-name value] val]
            (if (= port in)
              (let [new-in-params (assoc in-params port-name value)]
                (>! out-chan new-in-params)
                (recur new-in-params))
              (recur in-params))))))))
