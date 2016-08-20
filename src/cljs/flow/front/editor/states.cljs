(ns flow.front.editor.states
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.ratom :refer-macros [reaction]]
            [re-frame.core :refer [dispatch dispatch-sync subscribe
                                   register-handler register-sub debug]]
            [com.rpl.specter :refer [transform ALL select FIRST]]

            [flow.docs.graphs :as graphs]
            [flow.front.editor.utils :refer [node-position update-node-position
                                             node-window-position]]))

;;;;;;;;;;
;;; State ID Handler

(defn major-id [key]
  (first key))

(defn minor-id [key]
  (into [] (rest key)))

(defn full-id [major-id minor-id]
  (apply conj [major-id] minor-id))

;;;;;;;;;;
;;; State

(def ^:private id->state-handler (atom {}))

(defn register-state-handler [major-state-id state]
  (swap! id->state-handler assoc major-state-id state))

;;;;;;;;;;
;;; Helpers

(defn- call-finish [f db id params]
  (f db [id :finish] params))

(defn- call-switch [f db id params]
  (f db [id :switch] params))

(defn- call-event [f db id event-key params]
  (f db [id event-key] params))

(defn add-state [db id addition]
  (assoc db :state (merge {:major-id (major-id id)
                           :minor-id (minor-id id)}
                          addition)))

(defn remove-state [db id]
  (dissoc db :state))

(defn update-state [db id addition]
  (assoc db :state (merge (:state db)
                          addition)))

(defn state-for [db id]
  (let [major-id (major-id id)
        minor-id (minor-id id)
        state (:state db)]
    (if (and (= (:major-id state) major-id)
             (= (:minor-id state) minor-id))
      state
      nil)))

;;;;;;;;;;
;;; Sub

(register-sub
 :state-for
 (fn [db [_ id]]
   (reaction (state-for @db id))))

;;;;;;;;;;
;;; Handlers

(defn- request-finish [db params]
  (if-let [state (get-in db [:state])]
    (let [state-major-id (get-in db [:state :major-id])
          state-minor-id (get-in db [:state :minor-id])
          state-handler (get @id->state-handler state-major-id)
          finish-fn (:finish state-handler)]
      (if finish-fn
        (call-finish finish-fn db (full-id state-major-id state-minor-id) params)
        (dissoc db :state)))
    db))

(register-handler
 :request-finish
 (fn [db [_ params]]
   (request-finish db params)))

(register-handler
 :request-finish-only-for
 (fn [db [_ id params]]
   (if-let [state (get-in db [:state])]
     (if (and (= (major-id id) (get-in db [:state :major-id]))
              (= (minor-id id) (get-in db [:state :minor-id])))
       (request-finish db params)
       db)
     db)))

(defn- request-switch [db state-id params]
  (let [state-major-id (major-id state-id)
        state-minor-id (minor-id state-id)
        state-handler (get @id->state-handler state-major-id)
        switch-fn (:switch state-handler)]
    (if-let [state (get-in db [:state])]
      (let [db (request-finish db [:request-finish])]
        (if (and (nil? (get-in db [:state]))
                 switch-fn)
          (call-switch switch-fn db state-id params)
          db))
      (if switch-fn
        (call-switch switch-fn db state-id params)
        db))))

(register-handler
 :request-switch
 (fn [db [_ state-id params]]
   (request-switch db state-id params)))

(defn- emit-event [db event-key params]
  (if-let [state (get-in db [:state])]
    (let [state-major-id (get-in db [:state :major-id])
          state-minor-id (get-in db [:state :minor-id])
          state-handler (get @id->state-handler state-major-id)
          event-fn (get state-handler event-key)]
      (if event-fn
        (call-event event-fn db (full-id state-major-id state-minor-id) event-key params)
        db))
    db))

(register-handler
 :emit-event
 (fn [db [_ event-key params]]
   (emit-event db event-key params)))

(register-handler
 :emit-event-only-for
 (fn [db [_ id event-key params]]
   (if-let [state (get-in db [:state])]
     (if (and (= (major-id id) (get-in db [:state :major-id]))
              (= (minor-id id) (get-in db [:state :minor-id])))
       (emit-event db event-key params)
       db)
     db)))

;;;;;;;;;;
;;; Event Handlers

(register-state-handler
 :editing-name
 {:switch
  (fn [db [_ _]]
    (add-state db [:editing-name]
               {:value (get-in db [:target :name])}))
  :on-change
  (fn [db [_ _] {:keys [value]}]
    (update-state db [:editing-name] {:value value}))
  :finish
  (fn [db [_ _] {:keys [save?]}]
    (if save?
      (do
        (dispatch [:commit-graph])
        (-> (update-in db [:target :name] (fn [_]
                                            (get-in db [:state :value])))
            (remove-state [:editing-name])))
      (remove-state db [:editing-name])))})

(register-state-handler
 :dragging-window
 {:switch
  (fn [db [[_] _] {:keys [mouse-x mouse-y]}]
    (add-state db [:dragging-window]
               {:mouse-x mouse-x
                :mouse-y mouse-y}))
  :on-mouse-move
  (fn [db [[_] _] {:keys [mouse-x mouse-y]}]
    (let [state (state-for db [:dragging-window])
          dx (- mouse-x (:mouse-x state))
          dy (- mouse-y (:mouse-y state))]
      (-> db
          (assoc :window-offset {:x (- (or (get-in db [:window-offset :x]) 0) dx)
                                 :y (- (or (get-in db [:window-offset :y]) 0) dy)})
          (update-state [:dragging-window]
                        {:mouse-x mouse-x
                         :mouse-y mouse-y}))))
  :on-mouse-up
  (fn [db [[_ instance-id] _] _]
    (dispatch [:request-finish-only-for [:dragging-window]])
    db)
  :finish
  (fn [db [[_ instance-id] _] _]
    (remove-state db [:dragging-window]))})

(register-state-handler
 :dragging-node
 {:switch
  (fn [db [[_ node-id] _] {:keys [mouse-x mouse-y]}]
    (add-state db [:dragging-node node-id]
               {:mouse-x mouse-x
                :mouse-y mouse-y}))
  :on-mouse-move
  (fn [db [[_ node-id] _] {:keys [mouse-x mouse-y]}]
    (let [state (state-for db [:dragging-node node-id])
          dx (- mouse-x (:mouse-x state))
          dy (- mouse-y (:mouse-y state))
          p (node-position db node-id)]
      (-> (transform [:target :nodes ALL #(= (:id %) node-id)]
                     #(update-node-position
                       %
                       {:x (+ (:x p) dx)
                        :y (+ (:y p) dy)})
                     db)

          (update-state [:dragging-node node-id]
                        {:mouse-x mouse-x
                         :mouse-y mouse-y}))))
  :on-mouse-up
  (fn [db [[_ node-id] _] _]
    (dispatch [:request-finish-only-for [:dragging-node node-id]])
    db)
  :finish
  (fn [db [[_ node-id] _] _]
    (dispatch [:commit-graph])
    (remove-state db [:dragging-node node-id]))})

(defn- select-port [db k {:keys [mouse-x mouse-y]}]
  (let [port-keys (keys (k db))
        filtered
        (filter #(let [p (get-in db [k %])]
                   (and (<= (:left p) mouse-x (:right p))
                        (<= (:top p) mouse-y (:bottom p))))
                port-keys)
        selected (first filtered)]
    selected))

(defn- select-inport [db state]
  (select-port db :inport-window-positions state))

(defn- select-outport [db state]
  (select-port db :outport-window-positions state))

(register-state-handler
 :connecting
 {:switch
  (fn [db [_ _] {:keys [node-id port-name from-outport? mouse-x mouse-y]}]
    (add-state db [:connecting]
               {:node-id node-id
                :port-name port-name
                :from-outport? from-outport?
                :mouse-x mouse-x
                :mouse-y mouse-y}))
  :on-mouse-move
  (fn [db [_ _] {:keys [mouse-x mouse-y]}]
    (update-state db [:connecting]
                  {:mouse-x mouse-x
                   :mouse-y mouse-y}))
  :on-mouse-up
  (fn [db [_ _]]
    (dispatch [:request-finish-only-for [:connecting] {:save? true}])
    db)
  :finish
  (fn [db [_ _] {:keys [save?]}]
    (if save?
      (let [state (state-for db [:connecting])
            selected (if (:from-outport? state)
                       (select-inport db state)
                       (select-outport db state))]
        (if selected
          (dispatch [:update-graph
                     #(graphs/update-connection
                       %
                       (if (:from-outport? state)
                         {:from {:node (:node-id state)
                                 :outport (:port-name state)}
                          :to {:node (first selected)
                               :inport (second selected)}}
                         {:from {:node (first selected)
                                 :outport (second selected)}
                          :to {:node (:node-id state)
                               :inport (:port-name state)}}))]))))
    (remove-state db [:connecting]))})

(register-sub
 :connecting-port-window-position
 (fn
   [db [_]]
   (reaction
    (if-let [state (state-for @db [:connecting])]
      (if (:from-outport? state)
        (get-in @db [:outport-window-positions [(:node-id state) (:port-name state)]])
        (get-in @db [:inport-window-positions [(:node-id state) (:port-name state)]]))))))

(register-state-handler
 :binding
 {:switch
  (fn [db [_ _] {:keys [port-type port-name mouse-x mouse-y]}]
    (println "switching...")
    (add-state db [:binding]
               {:port-type port-type
                :port-name port-name
                :mouse-x mouse-x
                :mouse-y mouse-y}))
  :on-mouse-move
  (fn [db [_ _] {:keys [mouse-x mouse-y]}]
    (update-state db [:binding]
                  {:mouse-x mouse-x
                   :mouse-y mouse-y}))
  :on-mouse-up
  (fn [db [_ _]]
    (dispatch [:request-finish-only-for [:binding] {:save? true}])
    db)
  :finish
  (fn [db [_ _] {:keys [save?]}]
    (if save?
      (let [state (state-for db [:binding])
            selected (if (= (:port-type state) :inport)
                       (select-inport db state)
                       (select-outport db state))]
        (if selected
          (dispatch [:update-graph
                     #(if (= (:port-type state) :inport)
                        (graphs/update-inport
                         %
                         (let [inport (graphs/find-inport-by-name % (:port-name state))]
                           (assoc inport :bound
                                  {:node (first selected)
                                   :inport (second selected)})))
                        (graphs/update-outport
                         %
                         (let [outport (graphs/find-outport-by-name % (:port-name state))]
                           (assoc outport :bound
                                  {:node (first selected)
                                   :outport (second selected)}))))]))))
    (remove-state db [:binding]))})
