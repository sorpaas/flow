(ns flow.auth
  (:require [compojure.core :refer [GET POST PATCH PUT DELETE context routes] :as com]
            [compojure.response :refer [render]]
            [ring.util.response :refer [redirect response content-type] :as r]

            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]

            [flow.docs.users :as users]
            [flow.docs.graphs :as graphs]
            [flow.db :refer [db] :as db]
            [flow.utils :refer [generate-id]]

            [flow.pages :refer :all]))

(defn- login [req]
  (let [username (get-in req [:form-params "username"])
        password (get-in req [:form-params "password"])
        session (:session req)
        user (users/authenticate db {:username username} password)]
    (if user
      (let [next-url (get-in req [:query-params "next"] "/")
            updated-session (assoc session :identity (:id user))]
        (-> (redirect next-url)
            (assoc :session updated-session)))
      (response login-page))))

(defn- signup [req]
  (let [username (get-in req [:form-params "username"])
        password (get-in req [:form-params "password"])
        password-confirmation (get-in req [:form-params "password-confirmation"])
        session (:session req)
        graph (graphs/new-normal-graph)
        user {:id (generate-id)
              :username username
              :home (:id graph)}]
    (assert (= password password-confirmation))
    (assert (nil? (users/find-user-by-username db username)))
    (graphs/commit-graph! db graph)
    (users/commit-user! db user)
    (users/reset-password! db user password)
    (let [next-url (get-in req [:query-params "next"] "/")
          session (assoc session :identity (:id user))]
      (-> (redirect next-url)
          (assoc :session session)))))

(defn- logout [req]
  (-> (redirect "/login")
      (assoc :session {})))

(defn- unauthorized-handler [req metadata]
  (cond
    (authenticated? req)
    (-> (render "Not authorized." req)
        (assoc :status 403))

    :else
    (let [current-url (:uri req)]
      (redirect (format "/login?next=%s" current-url)))))

(defonce auth-backend (session-backend {:unauthorized-handler unauthorized-handler}))

(defn wrap-restriction [handler]
  (fn [req]
    (if-not (authenticated? req)
      (throw-unauthorized)
      (let [user (users/find-user-by-id db (get-in req [:session :identity]))]
        (handler (assoc req :user user))))))

(defn wrap-auth [handler]
  (-> handler
      (wrap-authorization auth-backend)
      (wrap-authentication auth-backend)))

(defn auth-routes []
  (routes
   (GET "/login" []
     (fn [req]
       (if (authenticated? req)
         (redirect (get-in req [:query-params :next] "/"))
         (response login-page))))

   (POST "/login" []
     #(login %))

   (GET "/logout" []
     #(logout %))

   (GET "/signup" []
     (fn [req]
       (if (authenticated? req)
         (redirect (get-in req [:query-params :next] "/"))
         (response signup-page))))

   (POST "/signup" []
     #(signup %))))
