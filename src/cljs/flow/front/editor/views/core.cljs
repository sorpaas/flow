(ns flow.front.editor.views.core
  (:require [reagent.core :as reagent :refer [atom]]
            [re-frame.core :refer [dispatch dispatch-sync subscribe]]
            [flow.front.editor.views.nodes :refer [node-view]]
            [flow.front.editor.views.connections :refer [connecting-view connection-view]]
            [flow.front.editor.views.graph :refer [name-textbox-view
                                                   search-new-node-view
                                                   graph-inports-view
                                                   graph-outports-view
                                                   graph-instances-view]]))

(defn about-links []
  [:div.about-links
   [:a {:href "/"} "Home"] " | "
   [:a {:href "/elements"} "Elements"]])

(defn editor-svg-view []
  (let [connections (subscribe [:connections])]
    (fn []
      [:svg.editor-svg
       [:g.connections
        (for [connection @connections]
          [connection-view connection])]
       [connecting-view]])))

(defn editor-view []
  (let [nodes (subscribe [:nodes])]
    (fn []
      [:div.editor
       {:on-mouse-move #(dispatch [:emit-event :on-mouse-move {:mouse-x (.-clientX %)
                                                               :mouse-y (.-clientY %)}])
        :on-mouse-up #(dispatch [:emit-event :on-mouse-up])
        :on-context-menu #(.preventDefault %)}
       [:div.dragger
        {:on-mouse-down #(dispatch [:request-switch [:dragging-window] {:mouse-x (.-clientX %)
                                                                        :mouse-y (.-clientY %)}])}]

       [:div.editor-top
        [:div.editor-header
         [name-textbox-view]
         [search-new-node-view]]
        [graph-inports-view]
        [graph-outports-view]
        [graph-instances-view]]
       [:div.editor-footer
        [about-links]]

       (for [node @nodes]
         ^{:key (str "instance-" (:id node))}
         [node-view node])
       [editor-svg-view]])))

