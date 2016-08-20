(ns flow.websocket
  (:require [mount.core :refer [defstate]]
            [compojure.core :refer [GET POST PATCH PUT DELETE context routes] :as com]
            [clojure.core.async :as async :refer [<! <!! >! >!! put! chan go go-loop close! alts! timeout]]
            [clojure.data :refer [diff]]

            [flow.db :refer [db] :as db]
            [flow.walker.core :refer [new-native-walker register-watcher! reset-coordinator!
                                      register-meta-watcher! delete-instance!
                                      create-instance! close-walker!] :as walker]
            [flow.walker.coordinators.normal :refer [normal-coordinator]]
            [flow.walker.coordinators.primitives :refer [primitive-coordinators]]
            [flow.walker.coordinators.misc :refer [misc-coordinators]]

            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]))

;;; Walker state

(defonce inport-states (atom {}))
(defonce outport-states (atom {}))

;;; Inside channel-socket ring-ajax-post ring-ajax-get-or-ws-handshake ch-chsk chsk-send! connected-uids(atom)

(defstate channel-socket
  :start (sente/make-channel-socket! sente-web-server-adapter {}))

(defn websocket-routes []
  (-> (routes
       (GET "/chsk" req ((:ajax-get-or-ws-handshake-fn channel-socket) req))
       (POST "/chsk" req ((:ajax-post-fn channel-socket) req)))))

(defstate uid-logger
  :start (add-watch (:connected-uids channel-socket) :connected-uids
                    (fn [_ _ old new]
                      (when (not= old new)
                        (infof "Connected uids change: %s" new)))))

(defn chsk-send! [uid msg] ((:send-fn channel-socket) uid msg))

(defn broadcast! [msg]
  (debugf "Broadcasting: %s" msg)
  (let [uids (:any @(:connected-uids channel-socket))]
    (doseq [uid uids]
      (chsk-send! uid msg))))

;;; Event message handlers

(defmulti -event-msg-handler :id)

(defn event-msg-handler [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler
  :default
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (debugf "Unhandled event: %s" event)
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

(defmethod -event-msg-handler
  :ping/echo
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid (:uid session)]
    (debugf "Ping message echoed from the client: %s" uid)))

(defmethod -event-msg-handler
  :walker/request-values
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid (:uid session)]
    (debugf "Requesting values from %s" uid)
    (when ?reply-fn
      (?reply-fn {:inport-values @inport-states
                  :outport-values @outport-states}))))

(defstate chsk-router
  :start (sente/start-server-chsk-router! (:ch-recv channel-socket)
                                          event-msg-handler)
  :stop (when-let [stop-f chsk-router] (stop-f)))

;;; Walker broadcaster

;;; TODO: Close walker broadcaster for defstate

(defn init-walker-broadcaster! [walker]
  (reset! inport-states {})
  (reset! outport-states {})
  (let [[watcher-id in out] (register-watcher! walker (constantly true))
        [meta-watcher-id meta-watcher-chan] (register-meta-watcher! walker)]
    (go
      (let [users (db/all-users db)]
        (doseq [user users]
          (when (:home user)
            (create-instance! walker [{:id (:id user)
                                       :type (:home user)}])))
        (loop [last-users users]
          (let [timeout-chan (timeout 10000)
                [val port] (alts! [in out meta-watcher-chan timeout-chan])]
            (cond
              (= port meta-watcher-chan)
              (when-let [[action route] val]
                (let [old-inport-values (get @inport-states route)
                      old-outport-values (get @outport-states route)]
                  (if (= action :create)
                    (do (swap! inport-states assoc route {})
                        (swap! outport-states assoc route {}))
                    (do (swap! inport-states dissoc route)
                        (swap! outport-states dissoc route)))
                  (broadcast! [:walker/inport-values-changed {:route route
                                                              :before old-inport-values
                                                              :after (if (= action :create) {} nil)}])
                  (broadcast! [:walker/outport-values-changed {:route route
                                                               :before old-outport-values
                                                               :after (if (= action :create) {} nil)}]))
                (recur last-users))

              (= port in)
              (when-let [[route sender-id port-name value] val]
                (let [old-values (get @inport-states route)
                      new-values (assoc old-values port-name value)]
                  (swap! inport-states assoc route new-values)
                  (broadcast! [:walker/inport-values-changed {:route route
                                                              :before old-values
                                                              :after new-values}]))
                (recur last-users))

              (= port out)
              (when-let [[route sender-id port-name value] val]
                (let [old-values (get @outport-states route)
                      new-values (assoc old-values port-name value)]
                  (swap! outport-states assoc route new-values)
                  (broadcast! [:walker/outport-values-changed {:route route
                                                               :before old-values
                                                               :after new-values}]))
                (recur last-users))

              (= port timeout-chan)
              (let [cur-users (db/all-users db)
                    [deleted-users created-users _] (diff (set last-users) (set cur-users))]
                (doseq [user deleted-users]
                  (when (:home user)
                    (delete-instance! walker [{:id (:id user)
                                               :type (:home user)}])))
                (doseq [user created-users]
                  (when (:home user)
                    (create-instance! walker [{:id (:id user)
                                               :type (:home user)}]))))

              :else
              nil)))))))

(defstate walker
  :start (let [walker (new-native-walker db)]
           (init-walker-broadcaster! walker)
           (reset-coordinator! walker :normal normal-coordinator)
           (doseq [[key val] primitive-coordinators]
             (reset-coordinator! walker key val))
           (doseq [[key val] misc-coordinators]
             (reset-coordinator! walker key val))
           walker)
  :stop (close-walker! walker))
