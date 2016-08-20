(ns flow.walker.coordinators.primitives
  (:require [clojure.core.async :as async :refer (<! <!! >! >!! put! chan go go-loop close! alts! timeout)]
            [clojure.data :refer [diff]]
            [clj-http.client :as client]
            [flow.walker.core :refer [Coordinator reset-route! put-outport! put-inport! walker-graph-db
                                      register-watcher! deregister-watcher!
                                      self-watcher-filter]]
            [flow.walker.coordinators.utils :refer [go-with-watchers reset-if-changed! find-graph-by-route]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]

            [flow.docs.graphs :refer [find-node-by-id
                                      find-graph-by-id
                                      find-graph-by-name
                                      find-connection
                                      wrap-relevants
                                      commit-graph!]]
            [flow.utils :refer [generate-id]]))

(defprotocol Forwarder
  (-forward [this walker in-params]))

(defn forwarder->coordinator [forwarder]
  (reify Coordinator
    (-new-instance [this walker route]
      (go-with-watchers walker
        [[self-watcher-id in out] (self-watcher-filter route)]

        (let [graph (find-graph-by-route walker route)
              init-out-params (-forward forwarder walker {})]
          (doseq [[key val] init-out-params]
            (put-outport! walker route :self key val))

          (loop [in-params {}
                 out-params {}]
            (when (not (reset-if-changed! walker route graph))
              (let [timeout-chan (timeout 10000)
                    [[route sender-id port-name value] port] (alts! [in out timeout-chan])]
                (cond
                  (= port in)
                  (let [new-in-params (assoc in-params port-name value)
                        new-out-params (-forward forwarder walker new-in-params)
                        [removed-keys new-keys existing-keys]
                        (diff (set (keys out-params)) (set (keys new-out-params)))]
                    (doseq [key removed-keys]
                      (put-outport! walker route
                                    :self key nil))
                    (doseq [key new-keys]
                      (put-outport! walker route
                                    :self key (get new-out-params key)))
                    (doseq [key existing-keys]
                      (when (not= (get out-params key) (get new-out-params key))
                        (put-outport! walker route
                                      :self key (get new-out-params key))))
                    (recur new-in-params new-out-params))

                  :else
                  (recur in-params out-params))))))))))

(def primitives
  [{:name "flow.core/one"
    :inports []
    :outports [{:name "out"}]
    :forwarder (reify Forwarder
                 (-forward [this walker in-params]
                   {"out" 1}))}

   {:name "flow.core/zero"
    :inports []
    :outports [{:name "out"}]
    :forwarder (reify Forwarder
                 (-forward [this walker in-params]
                   {"out" 0}))}

   {:name "flow.core/max"
    :inports [{:name "a"} {:name "b"}]
    :outports [{:name "out"}]
    :forwarder (reify Forwarder
                 (-forward [this walker in-params]
                   {"out" (max (or (get in-params "a") 0) (or (get in-params "b") 0))}))}

   {:name "flow.core/println"
    :inports [{:name "in"}]
    :outports []
    :forwarder (reify Forwarder
                 (-forward [this walker in-params]
                   (debugf "Println primitive: %s" (get in-params "in"))))}

   {:name "flow.core/nnot"
    :inports [{:name "in"}]
    :outports [{:name "out"}]
    :forwarder (reify Forwarder
                 (-forward [this walker in-params]
                   {"out" (if (= (get in-params "in") 1) 0 1)}))}

   {:name "tensorflow.example/mnist-data"
    :inports []
    :outports [{:name "out"}]
    :forwarder (reify Forwarder
                 (-forward [this walker in-params]
                   {"out" "preset:mnist"}))}
   {:name "tensorflow.example/example-trainee-graph"
    :inports []
    :outports [{:name "out"}]
    :forwarder (reify Forwarder
                 (-forward [this walker in-params]
                   {"out" "576a9ea5f4920317be67be8c"}))}
   {:name "tensorflow.example/example-minimize-graph"
    :inports []
    :outports [{:name "out"}]
    :forwarder (reify Forwarder
                 (-forward [this walker in-params]
                   {"out" "576a9f4bf49203191eeb8d0d"}))}
   {:name "tensorflow/gradient-descent-trainer"
    :inports [{:name "trainee"}
              {:name "minimize"}
              {:name "data"}]
    :outports []
    :forwarder (reify Forwarder
                 (-forward [this walker in-params]
                   (let [db (walker-graph-db walker)
                         trainee (get in-params "trainee")
                         minimize (get in-params "minimize")
                         data (get in-params "data")]
                     (if (and trainee minimize data)
                       (client/post (str "http://127.0.0.1:5000/train")
                                    {:form-params {:trainee (wrap-relevants db trainee)
                                                   :minimize (wrap-relevants db minimize)
                                                   :data data}
                                     :content-type :json})
                       {}))))}])

(def primitive-coordinators
  (into {} (map (fn [primitive]
                  [(:name primitive) (forwarder->coordinator (:forwarder primitive))]) primitives)))

(defn commit-primitives! [db]
  (doseq [graph primitives]
    (let [graph (-> graph
                    (dissoc :forwarder)
                    (assoc :primitive true
                           :worker "native"))
          graph-id (or (:id (find-graph-by-name db (:name graph)))
                       (generate-id))]
      (commit-graph! db (assoc graph :id graph-id)))))
