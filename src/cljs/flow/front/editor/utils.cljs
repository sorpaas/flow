(ns flow.front.editor.utils
  (:require [flow.docs.graphs :as graphs]
            [ajax.core :refer [GET POST PUT DELETE PATCH]]))

(def default-node-position {:x 20 :y 20})

(defn node-position [db node-id]
  (let [node (graphs/find-node-by-id (:target db) node-id)]
    (or (get-in node [:display :position]) default-node-position)))

(defn node-window-position [db node-id]
  (let [node-position (node-position db node-id)]
    {:x (- (:x node-position) (get-in db [:window-offset :x]))
     :y (- (:y node-position) (get-in db [:window-offset :y]))}))

(defn update-node-position [node position]
  (update-in node [:display :position] (fn [_] position)))

(defn search-graphs [{:keys [query handler]}]
  (POST (str "/api/graphs/search")
      {:params {:query query
                :wrap-relevants? false}
       :response-format :transit
       :handler handler}))
