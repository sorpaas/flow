(ns flow.docs.users
  (:require [schema.core :as s]
            [flow.docs.graphs :refer [GraphId GraphCollection find-graph-by-id]]
            [flow.utils :refer [generate-id]]))

(def Permission
  {:graph GraphId})

(def User
  {:id s/Str
   :username s/Str
   :home GraphId})

(defprotocol UserCollection
  (find-user-by-id [db id])
  (find-user-by-username [db username])
  (-reset-password! [db id password])
  (-authenticate [db id password])
  (-commit-user! [db user]))

(defn reset-password! [db user password]
  (if (string? user)
    (-reset-password! db user password)
    (-reset-password! db (:id (find-user-by-username db (:username user))) password)))

(defn authenticate [db user password]
  (let [user (if (string? user)
               (find-user-by-id db user)
               (find-user-by-username db (:username user)))]
    (if (and user (-authenticate db (:id user) password))
      user nil)))

(defn commit-user! [db user]
  (s/validate User user)
  (-commit-user! db user))
