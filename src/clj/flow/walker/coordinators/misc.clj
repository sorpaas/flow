(ns flow.walker.coordinators.misc
  (:require [clojure.core.async :as async :refer (<! <!! >! >!! put! chan go go-loop close! alts! timeout)]
            [flow.walker.core :refer [Coordinator reset-route! put-outport! put-inport! walker-graph-db
                                      register-watcher! deregister-watcher!
                                      self-watcher-filter]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [flow.walker.coordinators.utils :refer [go-with-watchers reset-if-changed! find-graph-by-route]]

            [flow.docs.graphs :refer [find-node-by-id
                                      find-graph-by-id
                                      find-graph-by-name
                                      find-connection
                                      commit-graph!]]
            [flow.utils :refer [generate-id]]
            [clj-http.client :as client]
            [clojure.data.json :as json]))

(def dumb-coordinator
  (reify Coordinator
    (-new-instance [this walker route]
      (debugf "WARNING: Dumb coordinator called for route %s." route))))

(def random-text-generator-coordinator
  (reify Coordinator
    (-new-instance [this walker route]
      (go
        (let [graph (find-graph-by-route walker route)]
          (loop []
            (when (not (reset-if-changed! walker route graph))
              (do (<! (timeout 1000))
                  (put-outport! walker route
                                :self "out"
                                (-> (client/get (str "http://www.schmipsum.com/ipsum/shakespeare/10")
                                                {:accept :json})
                                    :body
                                    json/read-str
                                    (get "ipsum")))
                  (recur)))))))))

(def misc-primitives
  [{:name "flow.misc/random-text-generator"
    :inports []
    :outports [{:name "out"}]}

   ; Tensorflow primitives
   {:name "tensorflow/softmax"
    :inports [{:name "in"}]
    :outports [{:name "out"}]}
   {:name "tensorflow/log"
    :inports [{:name "in"}]
    :outports [{:name "out"}]}
   {:name "tensorflow/msum"
    :inports [{:name "in"}]
    :outports [{:name "out"}]}
   {:name "tensorflow/time"
    :inports [{:name "a"}
              {:name "b"}]
    :outports [{:name "out"}]}
   {:name "tensorflow/mean"
    :inports [{:name "in"}]
    :outports [{:name "out"}]}])

(defn commit-misc-primitives! [db]
  (doseq [graph misc-primitives]
    (let [graph (-> graph
                    (dissoc :forwarder)
                    (assoc :primitive true
                           :worker "native"))
          graph-id (or (:id (find-graph-by-name db (:name graph)))
                       (generate-id))]
      (commit-graph! db (assoc graph :id graph-id)))))

(def misc-coordinators
  {"flow.misc/random-text-generator" random-text-generator-coordinator
   "tensorflow/softmax" dumb-coordinator
   "tensorflow/log" dumb-coordinator
   "tensorflow/msum" dumb-coordinator
   "tensorflow/time" dumb-coordinator
   "tensorflow/mean" dumb-coordinator})
