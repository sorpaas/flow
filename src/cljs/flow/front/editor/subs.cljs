(ns flow.front.editor.subs
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.ratom :refer-macros [reaction]]
            [re-frame.core :refer [register-sub subscribe]]
            [flow.front.editor.utils :refer [node-window-position]]
            [com.rpl.specter :refer [select transform ALL]]))

;;; Graph

(register-sub
 :graph
 (fn
   [db _]
   (reaction (get-in @db [:target]))))

(register-sub
 :relevant-graph
 (fn
   [db [_ graph-id]]
   (reaction (first (filter #(= (:id %) graph-id) (:relevants @db))))))

;;; Sub-graph items

(register-sub
 :name
 (fn
   [db _]
   (reaction (get-in @db [:target :name]))))

(register-sub
 :nodes
 (fn
   [db _]
   (reaction (get-in @db [:target :nodes]))))

(register-sub
 :inports
 (fn
   [db _]
   (reaction (get-in @db [:target :inports]))))

(register-sub
 :outports
 (fn
   [db _]
   (reaction (get-in @db [:target :outports]))))

(register-sub
 :connections
 (fn
   [db _]
   (reaction (get-in @db [:target :connections]))))

;;; Walker

(register-sub
 :instances
 (fn
   [db _]
   (reaction (let [subroutes (->> (filter (fn [[key val]]
                                         (some #(= (get-in @db [:target :id]) (:type %)) key))
                                       (merge (:inport-values @db)
                                              (:outport-values @db)))
                                  (map first))
                   routes (into []
                                (set (reduce concat []
                                             (map (fn [route]
                                                    (filter #(= (:type (last %)) (get-in @db [:target :id]))
                                                            (map (fn [x] (subvec route 0 (inc x))) (range (count route)))))
                                                  subroutes))))]
               routes))))

(register-sub
 :selected-route
 (fn
   [db _]
   (let [instances (subscribe [:instances])]
     (reaction (if (:selected-route @db)
                 (:selected-route @db)
                 (first @instances))))))

(register-sub
 :node-inport-value
 (fn
   [db [_ child-route inport-name]]
   (let [selected-route (subscribe [:selected-route])]
     (reaction (get (get-in @db [:inport-values (into [] (concat @selected-route [child-route]))]) inport-name)))))

(register-sub
 :node-outport-value
 (fn
   [db [_ child-route outport-name]]
   (let [selected-route (subscribe [:selected-route])]
     (reaction (get (get-in @db [:outport-values (into [] (concat @selected-route [child-route]))]) outport-name)))))

;;; Position management

(register-sub
 :node-window-position
 (fn
   [db [_ node-id]]
   (reaction (node-window-position @db node-id))))

(register-sub
 :inport-window-position
 (fn
   [db [_ node-id port-name]]
   (reaction (get-in @db [:inport-window-positions [node-id port-name]]))))

(register-sub
 :outport-window-position
 (fn
   [db [_ node-id port-name]]
   (reaction (get-in @db [:outport-window-positions [node-id port-name]]))))
