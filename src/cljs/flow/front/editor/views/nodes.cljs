(ns flow.front.editor.views.nodes
  (:require [reagent.core :as reagent :refer [atom]]
            [flow.front.editor.views.utils :refer [adjustable-textbox]]
            [flow.docs.graphs :as graphs]
            [flow.utils :refer [generate-id]]
            [flow.front.editor.routes :refer [nav!]]
            [re-frame.core :refer [dispatch dispatch-sync subscribe]]))

(defn port-id [prefix node-id port-name]
  (str "node-" (name node-id) "-" (name prefix) "-" port-name))

(defn port-component [port-type node port]
  (let [state (subscribe [:state-for [:binding]])
        graph-inport-value (subscribe [(if (= port-type :inport)
                                         :node-inport-value
                                         :node-outport-value) {:id (:id node)
                                                               :type (:type node)} (:name port)])]
    (fn [port-type node port]
      [:div {:class (name port-type)
             :on-mouse-down
             #(do (.preventDefault %)
                  (.stopPropagation %)
                  (dispatch [:request-switch
                             [:connecting]
                             {:node-id (:id node)
                              :port-name (:name port)
                              :from-outport? (= port-type :outport)
                              :mouse-x (.-clientX %)
                              :mouse-y (.-clientY %)}]))
             :on-context-menu #(.preventDefault %)}
       [:span (:name port) " (" @graph-inport-value ")"]])))

(defn inport-component [node inport]
  [port-component :inport node inport])

(defn outport-component [node outport]
  [port-component :outport node outport])

(defn update-port-position [prefix node-id port-name]
  (let [id (port-id prefix node-id port-name)
        dom (.getElementById js/document id)
        rect (.getBoundingClientRect dom)]
    (dispatch [(if (= prefix :inport) :reset-inport-window-position :reset-outport-window-position)
               node-id port-name
               {:x (if (= prefix :inport) (.-left rect) (.-right rect))
                :y (/ (+ (.-top rect) (.-bottom rect)) 2)
                :top (.-top rect)
                :bottom (.-bottom rect)
                :left (.-left rect)
                :right (.-right rect)}])))

(defn update-port-positions-factory [node]
  (let [relevant-graph (subscribe [:relevant-graph (:type node)])]
    (fn []
      (doseq [inport (:inports @relevant-graph)]
        (update-port-position :inport (:id node) (:name inport)))
      (doseq [outport (:outports @relevant-graph)]
        (update-port-position :outport (:id node) (:name outport))))))

(defn node-view [node]
  (let [relevant-graph (subscribe [:relevant-graph (:type node)])
        position (subscribe [:node-window-position (:id node)])
        update-port-positions (update-port-positions-factory node)]
    (reagent/create-class
     {:component-did-mount
      #(update-port-positions)
      :component-did-update
      #(update-port-positions)
      :reagent-render
      (fn []
        (let [relevant-graph @relevant-graph
              inports (:inports relevant-graph)
              outports (:outports relevant-graph)
              position @position]
          [:div.instance {:style {:left (:x position)
                                  :top (:y position)}}
           [:div.instance-prefix
            [:div.name {:on-mouse-down
                        #(do (if (= 2 (.-button %))
                               (dispatch [:update-graph (fn [x]
                                                          (graphs/delete-node x node))])
                               (dispatch [:request-switch
                                          [:dragging-node (:id node)]
                                          {:mouse-x (.-clientX %)
                                           :mouse-y (.-clientY %)}]))
                             (.stopPropagation %)
                             (.preventDefault %))
                        :on-context-menu #(.preventDefault %)}
             (or (:name relevant-graph) "(No name)")]
            (if (not (:primitive relevant-graph))
              [:a.uk-button.edit-node-type
               {:on-click #(nav! (str "/graphs/" (:id relevant-graph)))}
               [:i.fa.fa-pencil-square-o]])]
           [:div.ports
            (if-not (empty? inports)
              [:div.inports
               (for [inport inports]
                 ^{:key (port-id :inport (:id node) (:name inport))}
                 [:div.inport-wrapper {:id (port-id :inport (:id node) (:name inport))}
                  [inport-component node inport]])])
            (if-not (empty? outports)
              [:div.outports
               (for [outport outports]
                 ^{:key (port-id :outport (:id node) (:name outport))}
                 [:div.outport-wrapper {:id (port-id :outport (:id node) (:name outport))}
                  [outport-component node outport]])])]]))})))

