(ns flow.db
  (:require [mount.core :refer [defstate]]
            [monger.collection :as mc]
            [monger.core :as mg]
            [schema.core :as s]
            [flow.docs.users :refer [User UserCollection find-user-by-id find-user-by-username]]
            [flow.docs.graphs :refer [Graph GraphCollection find-graph-by-id relevant-graphs commit-graph!]]
            [flow.walker.coordinators.primitives :refer [commit-primitives!]]
            [flow.walker.coordinators.misc :refer [commit-misc-primitives!]]
            [buddy.hashers :as hashers])
  (:import org.bson.types.ObjectId))

(defn id->objectid [value]
  (-> value
      (assoc :_id (ObjectId. (:id value)))
      (dissoc :id)))

(defn objectid->id [value]
  (-> value
      (assoc :id (.toHexString (:_id value)))
      (dissoc :_id)))

(defn- graph->bson [graph]
  (id->objectid graph))

(defn- bson->graph [graph]
  (objectid->id graph))

(defn- bson->user [value]
  (objectid->id (dissoc value :password-digest)))

(defn- user->bson [value password-digest]
  (id->objectid (assoc value :password-digest password-digest)))

(defrecord MongoDatabase [conn db]
  GraphCollection
  (find-graph-by-id [this id]
    (if-let [bson (mc/find-one-as-map db "graphs" {:_id (ObjectId. id)})]
      (bson->graph bson)))
  (find-graph-by-name [this name]
    (if-let [bson (mc/find-one-as-map db "graphs" {:name name})]
      (bson->graph bson)))
  (-commit-graph! [this graph]
    (if (some? (find-graph-by-id this (:id graph)))
      (let [bson (graph->bson graph)]
        (mc/update-by-id db "graphs" (:_id bson) bson))
      (mc/insert db "graphs" (graph->bson graph))))

  UserCollection
  (find-user-by-id [this id]
    (if-let [bson (mc/find-one-as-map db "users" {:_id (ObjectId. id)})]
      (bson->user bson)))
  (find-user-by-username [this username]
    (if-let [bson (mc/find-one-as-map db "users" {:username username})]
      (bson->user bson)))
  (-reset-password! [this id password]
    (let [user (find-user-by-id this id)
          password-digest (hashers/encrypt password)]
      (mc/update-by-id db "users" (ObjectId. (:id user)) (user->bson user password-digest))))
  (-authenticate [this id password]
    (let [bson (mc/find-one-as-map db "users" {:_id (ObjectId. id)})]
      (and bson (hashers/check password (:password-digest bson)))))
  (-commit-user! [this user]
    (if-let [password-digest (:password-digest (mc/find-one-as-map db "users" {:_id (ObjectId. (:id user))}))]
      (mc/update-by-id db "users" (:id user) (user->bson user password-digest))
      (mc/insert db "users" (user->bson user nil)))))

(defn initialize-db [db-name]
  (let [conn (mg/connect)
        db-raw (mg/get-db conn db-name)
        db (MongoDatabase. conn db-raw)]
    (mc/create-index db-raw "graphs" {"name" 1} {"unique" true
                                             "partialFilterExpression"
                                             { "name" { "$exists" true }}})
    (mc/create-index db-raw "users" {"username" 1} {"unique" true})
    (commit-primitives! db)
    (commit-misc-primitives! db)
    db))

(defn deinitialize-db [db]
  (mg/disconnect (:conn db)))

(defstate db
  :start (initialize-db "flow-dev")
  :stop (deinitialize-db db))

(defn search-graph [db query]
  (if-let [bsons (mc/find-maps (:db db) "graphs" query)]
    (map bson->graph bsons)))

(defn all-users [db]
  (let [bsons (mc/find-maps (:db db) "users")]
    (map bson->user bsons)))
