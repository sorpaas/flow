(ns flow.front.editor.core
  (:require [js.reload]
            [mount.core :as mount]
            [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]

            [reagent.core :as reagent :refer [atom]]
            [re-frame.core :refer [dispatch dispatch-sync subscribe]]

            [flow.front.editor.routes :refer [get-token nav!]]

            [flow.front.editor.websocket]

            [flow.front.editor.handlers]
            [flow.front.editor.subs]
            [flow.front.editor.states]

            [flow.front.editor.views.core :refer [editor-view]])
  (:require-macros [mount.core :refer [defstate]]))

(nav! (get-token))

(defstate app
  :start (reagent/render [#'editor-view]
                         (js/document.getElementById "app")))

(defn start! []
  (enable-console-print!)
  (timbre/set-level! :debug)
  (mount/start))
