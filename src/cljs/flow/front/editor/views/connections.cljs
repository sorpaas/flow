(ns flow.front.editor.views.connections
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.ratom :refer-macros [reaction]]
            [re-frame.core :refer [dispatch dispatch-sync subscribe
                                   register-handler register-sub debug]]
            [flow.docs.graphs :as graphs]
            [com.rpl.specter :refer [transform ALL select FIRST]]))

;;; Cubic Bezier
(def edge-curve 72)
(defn cubic-bezier-intermediate [sx sy ex ey]
  (if (< (- ex 5) sx)
    (let [curve-factor (* (- sx ex) (/ edge-curve 200))]
      (if (< (Math/abs (- ey sy)) (/ edge-curve 2))
        [(+ sx curve-factor)
         (- sy curve-factor)
         (- ex curve-factor)
         (- ey curve-factor)]
        (if (> ey sy)
          [(+ sx curve-factor)
           (+ sy curve-factor)
           (- ex curve-factor)
           (- ey curve-factor)]
          [(+ sx curve-factor)
           (- sy curve-factor)
           (- ex curve-factor)
           (+ ey curve-factor)])))
    (let [halfway (/ (- ex sx) 2)]
      [(+ sx halfway)
       sy
       (+ sx halfway)
       ey])))

(defn cubic-bezier-path-dstr [sx sy ex ey]
  (let [intermediate (cubic-bezier-intermediate sx sy ex ey)]
    (str "M" sx "," sy " " "C"
         (nth intermediate 0) "," (nth intermediate 1) " "
         (nth intermediate 2) "," (nth intermediate 3) " "
         ex "," ey)))

(defn generic-connection-component [params start end]
  (let [sx (:x start) sy (:y start)
        ex (:x end) ey (:y end)
        path-dstr (cubic-bezier-path-dstr sx sy ex ey)]
    (if (and start end)
      [:g.connection params
       [:g.port {:transform (str "translate(" sx "," sy ")")}
        [:circle.port-circle-bg {:r 2.5}]
        [:circle.port-circle-small {:r 2.5}]]
       [:g.port {:transform (str "translate(" ex "," ey ")")}
        [:circle.port-circle-bg {:r 2.5}]
        [:circle.port-circle-small {:r 2.5}]]
       [:g.edge
        [:path.edge-bg {:d path-dstr}]
        [:path.edge-fg {:d path-dstr}]]])))

(defn connecting-view []
  (let [state (subscribe [:state-for [:connecting]])
        connecting-port-window-position (subscribe [:connecting-port-window-position])]
    (fn []
      (if @state
        (let [mouse-position {:x (:mouse-x @state) :y (:mouse-y @state)}
              connecting-port-window-position @connecting-port-window-position
              from-outport? (:from-outport? @state)]
          [generic-connection-component {}
           (if from-outport? connecting-port-window-position mouse-position)
           (if from-outport? mouse-position connecting-port-window-position)])))))

(defn connection-view [conn]
  (let [start (subscribe [:outport-window-position (get-in conn [:from :node]) (get-in conn [:from :outport])])
        end (subscribe [:inport-window-position (get-in conn [:to :node]) (get-in conn [:to :inport])])]
    (fn []
      [generic-connection-component
       {:on-mouse-down #(do (.preventDefault %)
                            (if (= 2 (.-button %))
                              (dispatch [:update-graph (fn [x] (graphs/delete-connections x (:from conn) (:to conn)))])))}
       @start @end])))
