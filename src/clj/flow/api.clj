(ns flow.api
  (:require [compojure.core :refer [GET POST PATCH PUT DELETE context routes] :as com]
            [ring.util.response :refer [redirect response] :as r]
            [ring.middleware.format :refer [wrap-restful-format]]

            [flow.docs.users :as users]
            [flow.docs.graphs :as graphs]
            [flow.db :refer [db] :as db]
            [flow.utils :refer [generate-id]]

            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]))

(defn api-routes []
  (-> (routes
       (POST "/graphs/search" []
         (fn [req]
           (let [params (:params req)
                 user (:user req)
                 wrap-relevants? (get params :wrap-relevants)
                 query (:query params)]
             (if (empty? query)
               (->> (:permissions user)
                    (map :graph)
                    (map #(graphs/find-graph-by-id db %))
                    (map (fn [element]
                           (if wrap-relevants?
                             (graphs/wrap-relevants db element)
                             element)))
                    (response))
               (->> (db/search-graph db query)
                    (map (fn [element]
                           (if wrap-relevants?
                             (graphs/wrap-relevants db element)
                             element)))
                    (response))))))

       (PUT "/graphs" []
         (fn [req]
           (let [params (:params req)
                 user (:user req)
                 wrap-relevants? (get params :wrap-relevants)
                 element (graphs/new-normal-graph)]
             (graphs/commit-graph! db element)
             (response (if wrap-relevants?
                         (graphs/wrap-relevants db element)
                         element)))))

       (GET "/graphs/:graph-id" [graph-id]
         (fn [req]
           (let [params (:params req)
                 user (:user req)
                 wrap-relevants? (get params :wrap-relevants)
                 element (graphs/find-graph-by-id db graph-id)]
             (response (if wrap-relevants?
                         (graphs/wrap-relevants db element)
                         element)))))

       (PATCH "/graphs" []
         (fn [req]
           (graphs/commit-graph! db (:params req))
           (response nil))))

      (wrap-restful-format :formats [:json-kw :edn :msgpack-kw
                                     :yaml-kw :yaml-in-html
                                     :transit-json :transit-msgpack])))
