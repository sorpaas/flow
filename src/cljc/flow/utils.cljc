(ns flow.utils
  #?(:cljs (:require [objectid]
                     [cljs.reader :as reader]))
  #?@(:clj [(:import org.bson.types.ObjectId)]))

(defn include-data [m]
  (let [data (into {} (map (fn [[k v]]
                             [(keyword (str "data-" (name k))) (pr-str v)])
                           m))]
    [:div (merge data
                 {:style {:display "hidden"}
                  :id "server-originated-data"})]))

#?(:cljs
   (defn get-data
     [tag]
     (-> (.getElementById js/document "server-originated-data")
         (.getAttribute (str "data-" (name tag)))
         (str)
         (reader/read-string))))

(defn generate-id []
  #?(:cljs (objectid/generate))
  #?(:clj (.toHexString (ObjectId.))))
