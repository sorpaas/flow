(ns flow.server
  (:require [mount.core :refer [defstate]]
            [compojure.core :refer [GET POST PATCH PUT DELETE context routes] :as com]
            [compojure.route :as route]
            [ring.util.response :refer [redirect response content-type] :as r]
            [org.httpkit.server :refer [run-server]]

            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.reload :refer [wrap-reload]]

            [flow.api :refer [api-routes]]
            [flow.auth :refer [wrap-auth wrap-restriction auth-routes]]
            [flow.websocket :refer [websocket-routes]]
            [flow.db :refer [db] :as db]
            [flow.docs.graphs :as graphs]
            [flow.docs.users :as users]
            [flow.utils :refer [generate-id]]

            [flow.pages :refer :all]
            [clojure.string :as str]))

(defn user-routes []
  (routes
   (GET "/graphs/home" []
     (fn [req]
       (let [user (:user req)
             graph (if (:home user) (graphs/find-graph-by-id db (:home user)) (graphs/new-normal-graph))]
         (users/commit-user! db (assoc user :home (:id graph)))
         (graphs/commit-graph! db graph)
         (redirect (str "/graphs/" (:id graph))))))

   (GET "/graphs/:graph-id" [graph-id]
     (fn [req]
       (response (graph-page db "/js/editor.js"))))

   (context "/api" [] (api-routes))))

(defn app-routes []
  (routes
   (route/resources "/js" {:root "js"})
   (route/resources "/css" {:root "css"})
   (route/resources "/images" {:root "images"})

   (GET "/" []
     (fn [req]
       (response (index-page (get-in req [:session :identity])))))

   (auth-routes)
   (websocket-routes)
   (wrap-restriction (user-routes))

   (route/not-found "Page not found.")))

(defn app []
  (-> (app-routes)
      (wrap-auth)
      (wrap-keyword-params)
      (wrap-params)
      (wrap-session {:store (cookie-store {:key "topsecretshouldb"})})
      (wrap-reload)))

(defstate server
  :start (run-server (app) {:port 10555 :join? false})
  :stop (server))
