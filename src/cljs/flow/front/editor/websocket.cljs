(ns flow.front.editor.websocket
  (:require [cljs.core.async :as async :refer (<! >! put! chan)]
            [re-frame.core :refer [register-handler dispatch path trim-v after debug]]
            [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]
            [taoensso.sente  :as sente :refer (cb-success?)])
  (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)]
                   [mount.core :refer [defstate]]))

;;; Inside channel-socket chsk ch-chsk chsk-send! chsk-state

(defonce channel-socket (sente/make-channel-socket-client! "/chsk" {:type :auto}))

(defn chsk-send! [& params] (apply (:send-fn channel-socket) params))

;;;; Sente event handlers

(defmulti -event-msg-handler :id)

(defn event-msg-handler
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler
  :default
  [{:as ev-msg :keys [event]}]
  (debugf "Unhandled event: %s" event))

(defmethod -event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (if (:first-open? ?data)
    (dispatch [:fetch-walker-values])))

(defmethod -event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (if ?data
    (let [[key {:keys [route before after]}] ?data]
      (if (= key :walker/inport-values-changed)
        (dispatch [:update-inport-values route after])
        (dispatch [:update-outport-values route after])))))

(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (debugf "Handshake: %s" ?data)))

(defstate chsk-router
  :start (let [ch-chsk (:ch-recv channel-socket)]
           (sente/start-client-chsk-router! ch-chsk
                                            event-msg-handler)))
