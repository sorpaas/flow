(ns flow.walker.coordinators.normal
  (:require [clojure.core.async :as async :refer (<! <!! >! >!! put! chan go go-loop close! alts! timeout)]
            [clojure.data :refer [diff]]
            [flow.walker.core :refer [Coordinator reset-route! put-outport! put-inport! walker-graph-db
                                      reset-direct-subroutes! register-watcher!
                                      self-watcher-filter direct-subroutes-watcher-filter deregister-watcher!]]
            [flow.walker.coordinators.utils :refer [go-with-watchers reset-if-changed! find-graph-by-route]]
            [flow.docs.graphs :refer [find-node-by-id
                                      find-graph-by-id
                                      find-inport-by-name
                                      find-outport-by-name
                                      find-connections
                                      commit-graph!]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [flow.utils :refer [generate-id]]))

(defn- route-of-node [parent-route node]
  (into [] (concat parent-route [{:id (:id node)
                                  :type (:type node)}])))

(defn- normal-subroutes [type route]
  (into [] (map #(route-of-node route %) (:nodes type))))

(def normal-coordinator
  (reify Coordinator
    (-new-instance [this walker parent-route]
      (go-with-watchers walker
        [[self-watcher-id in out] (self-watcher-filter parent-route)
         [direct-subroutes-watcher-id subin subout] (direct-subroutes-watcher-filter parent-route)]

        (let [graph (find-graph-by-route walker parent-route)
              subroutes (normal-subroutes graph parent-route)]
          (reset-direct-subroutes! walker parent-route subroutes)

          (loop []
            (when (not (reset-if-changed! walker parent-route graph))
              (let [timeout-chan (timeout 1000)
                    [[route sender-id port-name value] port] (alts! [in out subin subout timeout-chan])]

                (cond
                  (= port in)
                  (let [inport (find-inport-by-name graph port-name)
                        bounded-node-id (get-in inport [:bound :node])
                        bounded-port-name (get-in inport [:bound :inport])]
                    (if bounded-node-id
                      (let [bounded-node (find-node-by-id graph bounded-node-id)
                            bounded-node-route (route-of-node parent-route bounded-node)]
                        (put-inport! walker bounded-node-route
                                     :parent bounded-port-name value))))

                  (= port subout)
                  (let [from-node-id (:id (last route))
                        connections (find-connections graph {:node from-node-id
                                                             :outport port-name} :all)]
                    (doseq [connection connections]
                      (let [to-node-id (get-in connection [:to :node])
                            to-port-name (get-in connection [:to :inport])
                            to-node (find-node-by-id graph to-node-id)
                            to-node-route (route-of-node parent-route to-node)]
                        (put-inport! walker to-node-route
                                     from-node-id to-port-name value)))
                    (doseq [outport (:outports graph)]
                      (when (and (= (get-in outport [:bound :node]) from-node-id)
                                 (= (get-in outport [:bound :outport]) port-name))
                        (put-outport! walker parent-route
                                      :self (:name outport) value))))

                  :else
                  (recur))))))))))
