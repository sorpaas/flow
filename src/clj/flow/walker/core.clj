(ns flow.walker.core
  (:require [clojure.core.async :as async :refer (<! <!! >! >!! put! chan go go-loop close! mult mix tap admix)]
            [clojure.data :refer [diff]]
            [flow.docs.graphs :refer [find-node-by-id
                                      find-graph-by-id
                                      find-connection]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [flow.utils :refer [generate-id]]))

;; (def Route
;;   [{:id s/Str
;;     :type GraphId}])

(def buffer-size 10)

(defprotocol Coordinator
  (-new-instance [this walker route]))

;; Watchable is a channel

(defprotocol WatcherManagerInternal
  (-on-create-route [this route in-chan out-chan])
  (-on-delete-route [this route]))

(defprotocol WatcherManager
  (register-watcher! [this filter-fn])
  (register-meta-watcher! [this])
  (deregister-watcher! [this id])
  (deregister-meta-watcher! [this id]))

(defrecord NativeWatcherManager [channels watchers meta-watchers]
  WatcherManagerInternal
  (-on-create-route [this route in-chan out-chan]
    (doseq [[meta-watcher-id meta-watcher-chan] @meta-watchers]
      (put! meta-watcher-chan [:create route]))
    (let [in-mult (mult in-chan)
          out-mult (mult out-chan)]
      (swap! channels assoc route {:in in-mult
                                   :out out-mult})
      (doseq [[id {:keys [filter-fn in out]}] @watchers]
        (assert (and (not (nil? in)) (not (nil? out))))
        (when (filter-fn route)
          (let [in-mult-chan (chan buffer-size)
                out-mult-chan (chan buffer-size)]
            (tap in-mult in-mult-chan)
            (tap out-mult out-mult-chan)
            (admix in in-mult-chan)
            (admix out out-mult-chan))))
      true))

  (-on-delete-route [this route]
    (doseq [[meta-watcher-id meta-watcher-chan] @meta-watchers]
      (put! meta-watcher-chan [:delete route])))

  WatcherManager
  (register-watcher! [this filter-fn]
    (let [final-in (chan buffer-size) final-out (chan buffer-size)
          final-in-mixed (mix final-in)
          final-out-mixed (mix final-out)
          id (generate-id)]
      (swap! watchers assoc id {:filter-fn filter-fn
                                :in final-in-mixed
                                :out final-out-mixed})
      (doseq [[route {:keys [in out]}] @channels]
        (assert (and (not (nil? in)) (not (nil? out))))
        (when (filter-fn route)
          (let [in-mult-chan (chan buffer-size)
                out-mult-chan (chan buffer-size)]
            (tap in in-mult-chan)
            (tap out out-mult-chan)
            (admix final-in-mixed in-mult-chan)
            (admix final-out-mixed out-mult-chan))))
      [id final-in final-out]))

  (deregister-watcher! [this id]
    (let [{:keys [filter-fn in out]} (get @watchers id)]
      (swap! watchers dissoc id)
      id))

  (register-meta-watcher! [this]
    (let [id (generate-id)
          watcher-chan (chan buffer-size)]
      (swap! meta-watchers assoc id watcher-chan)
      [id watcher-chan]))

  (deregister-meta-watcher! [this id]
    (swap! meta-watchers dissoc id)
    nil))

(defn new-native-watcher-manager []
  (map->NativeWatcherManager {:channels (atom {})
                              :watchers (atom {})
                              :meta-watchers (atom {})}))

(defprotocol Walker
  (reset-coordinator! [this type coordinator])
  (coordinator-for [this type])

  (walker-graph-db [this])

  (put-inport! [this route sender-id port-name value])
  (put-outport! [this route sender-id port-name value])
  (reset-direct-subroutes! [this parent subroutes])
  (reset-route! [this route])

  (all-routes [this])

  (close-walker! [this])
  (create-instance! [this route])
  (delete-instance! [this route]))

;; (def WalkerChannelMap
;;   [{:inport {:chan s/Any :mult s/Any :direct-subroutes s/Any}
;;     :outport {:chan s/Any :mult s/Any :direct-subroutes s/Any}}])

;;; Helper functions

(defn- vec-starts-with? [v subv]
  (if (> (count subv) (count v))
    false
    (= (subvec v 0 (count subv)) subv)))

(defn direct-subroute? [route subroute]
  (and (= (count subroute) (inc (count route)))
       (vec-starts-with? subroute route)))

(defn- direct-subroutes [walker route]
  (into [] (filter #(direct-subroute? route %) (keys @(:channels walker)))))

(defn- create-route! [walker route]
  (let [graph-id (:type (last route))
        coordinator (coordinator-for walker graph-id)
        inport-chan (chan)
        outport-chan (chan)]
    (swap! (:channels walker) assoc route {:in inport-chan
                                           :out outport-chan})
    (-on-create-route (:watcher-manager walker) route inport-chan outport-chan)
    (-new-instance coordinator walker route)))

(defn- delete-route! [walker route]
  (let [{:keys [in out]} (get-in @(:channels walker) [route])]
    (swap! (:channels walker) dissoc route)
    (-on-delete-route (:watcher-manager walker) route)
    (close! in)
    (close! out)))

(defn- delete-route-recursive! [walker route]
  (doseq [route (filter #(vec-starts-with? % route) (keys @(:channels walker)))]
    (delete-route! walker route)))

(defn self-watcher-filter [route]
  #(= route %))

(defn direct-subroutes-watcher-filter [route]
  #(direct-subroute? route %))

;;; Implementation

(defrecord NativeWalker [graph-db channels
                         coordinators watcher-manager
                         route-watchers]
  WatcherManager
  (register-watcher! [this filter-fn]
    (register-watcher! watcher-manager filter-fn))

  (register-meta-watcher! [this]
    (register-meta-watcher! watcher-manager))

  (deregister-watcher! [this id]
    (deregister-watcher! watcher-manager id))

  (deregister-meta-watcher! [this id]
    (deregister-meta-watcher! watcher-manager id))

  Walker
  (reset-coordinator! [this type coordinator]
    (swap! coordinators assoc type coordinator))

  (coordinator-for [this type]
    (let [graph-db (walker-graph-db this)
          graph (find-graph-by-id graph-db type)]
      (if (not (:primitive graph))
        (get @coordinators :normal)
        (get @coordinators (:name graph)))))

  (walker-graph-db [this] graph-db)

  (put-inport! [this route sender-id port-name value]
    (let [port-chan (get-in @channels [route :in])]
      (go (>! port-chan [route sender-id port-name value]))))

  (put-outport! [this route sender-id port-name value]
    (let [port-chan (get-in @channels [route :out])]
      (go (>! port-chan [route sender-id port-name value]))))

  (reset-direct-subroutes! [this parent subroutes]
    (let [[routes-to-be-deleted routes-to-be-created _] (diff (set (direct-subroutes this parent)) (set subroutes))]
      (doseq [route routes-to-be-deleted]
        (delete-route-recursive! this route))
      (doseq [route routes-to-be-created]
        (create-route! this route))))

  (all-routes [this]
    (keys @channels))

  (reset-route! [this route]
    (delete-route-recursive! this route)
    (create-route! this route))

  (close-walker! [this]
    (doseq [toplevel-route (filter #(= (count %) 1) (keys @channels))]
      (delete-instance! this toplevel-route)))

  (create-instance! [this toplevel-route]
    (let [route toplevel-route]
      (when-not (get @channels route)
        (create-route! this route))
      route))

  (delete-instance! [this toplevel-route]
    (let [route toplevel-route]
      (when (get @channels route)
        (delete-route-recursive! this route))
      route)))

(defn new-native-walker [graph-db]
  (map->NativeWalker {:graph-db graph-db
                      :channels (atom {})
                      :coordinators (atom {})
                      :watcher-manager (new-native-watcher-manager)}))
