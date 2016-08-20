(ns flow.front.editor.handlers
  (:require [reagent.core :as reagent :refer [atom]]
            [re-frame.core :refer [register-handler dispatch path trim-v after debug]]
            [flow.utils :refer [get-data generate-id]]
            [flow.front.editor.utils :refer [search-graphs]]
            [flow.docs.graphs :as graphs]
            [flow.front.editor.websocket :refer [chsk-send!]]
            [ajax.core :refer [GET POST PUT DELETE PATCH]]
            [schema.core :as s]
            [com.rpl.specter :refer [transform ALL select FIRST]]))

;;; Graph related

(register-handler
 :initialize
 (fn [_ [_ id]]
   (let [init-db {:target nil
                  :relevants nil
                  :inport-window-positions {}
                  :outport-window-positions {}
                  :inport-values {}
                  :outport-values {}
                  :window-offset {:x 0 :y 0}}]
     (dispatch [:fetch-graph id])
     (dispatch [:fetch-walker-values])
     init-db)))

(register-handler
 :fetch-graph
 (fn [db [_ id]]
   (GET (str "/api/graphs/" id)
       {:handler #(dispatch [:reset-graph %])
        :params {:wrap-relevants true}
        :response-format :transit})
   db))

(register-handler
 :reset-graph
 (fn [db [_ value]]
   (merge db {:target (:target value)
              :relevants (:relevants value)})))

(register-handler
 :update-graph
 debug
 (fn [db [_ f]]
   (let [new-graph (f (:target db))]
     (dispatch [:commit-graph])
     (assoc db :target new-graph))))

(register-handler
 :commit-graph
 (fn [db _]
   (let [target (:target db)]
     (PATCH (str "/api/graphs")
            {:handler #(dispatch [:fetch-graph (get-in db [:target :id])])
             :error-handler #(dispatch [:fetch-graph (get-in db [:target :id])])
             :params target
             :response-format :transit}))
   db))

;;; Watcher

(register-handler
 :fetch-walker-values
 (fn [db _]
   (chsk-send! [:walker/request-values] 5000
               (fn [cb-reply]
                 (dispatch [:reset-inport-values (:inport-values cb-reply)])
                 (dispatch [:reset-outport-values (:outport-values cb-reply)])))
   db))

(register-handler
 :reset-inport-values
 debug
 (fn [db [_ inport-values]]
   (assoc db :inport-values inport-values)))

(register-handler
 :reset-outport-values
 debug
 (fn [db [_ outport-values]]
   (assoc db :outport-values outport-values)))

(register-handler
 :update-inport-values
 debug
 (fn [db [_ route values]]
   (update-in db [:inport-values route] (fn [_] values))))

(register-handler
 :update-outport-values
 debug
 (fn [db [_ route values]]
   (update-in db [:outport-values route] (fn [_] values))))

(register-handler
 :switch-to-instance
 (fn [db [_ route]]
   (assoc db :selected-route route)))

;;; Subgraph

(register-handler
 :new-subgraph
 (fn [db _]
   (PUT (str "/api/graphs")
       {:handler (fn [subgraph]
                   (dispatch [:update-graph #(graphs/update-node % {:id (generate-id)
                                                                    :type (:id subgraph)})]))
        :response-format :transit})
   db))

;;; Position Hanlder

(register-handler
 :reset-inport-window-position
 (fn [db [_ node-id port-name value]]
   (assoc-in db [:inport-window-positions [node-id port-name]] value)))

(register-handler
 :reset-outport-window-position
 (fn [db [_ node-id port-name value]]
   (assoc-in db [:outport-window-positions [node-id port-name]] value)))
