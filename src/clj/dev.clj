(ns dev
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :as tn]
            [mount.core :as mount :refer [defstate]]
            [mount.tools.graph :refer [states-with-deps]]
            [robert.hooke :refer [add-hook clear-hooks]]
            [clojure.string :refer [split]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]

            [flow.server]
            [flow.websocket]
            [flow.db :refer [db]]))

;;; Catching core.async exceptions

(Thread/setDefaultUncaughtExceptionHandler
 (reify Thread$UncaughtExceptionHandler
   (uncaughtException [_ thread throwable]
     (debugf "Caught exception: %s" throwable)
     (.printStackTrace throwable))))

;;; Logging

(alter-meta! *ns* assoc ::load false)

(defn- f-to-action [f {:keys [status]}]
  (let [fname (-> (str f)
                  (split #"@")
                  first)]
    (case fname
      "mount.core$up" (when-not (:started status) :up)
      "mount.core$down" (when-not (:stopped status) :down)
      :noop)))

(defn whatcha-doing? [action]
  (case action
    :up ">> starting"
    :down "<< stopping"
    false))

(defn log-status [f & args]
  (let [[state-name state] args
        action (f-to-action f state)]
    (when-let [taking-over-the-world (whatcha-doing? action)]
      (infof (str taking-over-the-world ".. " state-name)))
    (apply f args)))

(defonce lifecycle-fns
  #{#'mount.core/up
    #'mount.core/down})

(defn without-logging-status []
  (doall (map #(clear-hooks %) lifecycle-fns)))

;; this is the one to use:

(defn with-logging-status []
  (without-logging-status)
  (doall (map #(add-hook % log-status) lifecycle-fns)))

;;; Lifecycle

(defn start []
  (with-logging-status)
  (mount/start))

(defn stop []
  (mount/stop))

(defn refresh []
  (stop)
  (tn/refresh))

(defn refresh-all []
  (stop)
  (tn/refresh-all))

(defn reset []
  (stop)
  (tn/refresh :after 'dev/start))
