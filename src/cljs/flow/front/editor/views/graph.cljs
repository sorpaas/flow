(ns flow.front.editor.views.graph
  (:require [reagent.core :as reagent :refer [atom]]
            [re-frame.core :refer [dispatch dispatch-sync subscribe]]
            [flow.utils :refer [generate-id]]
            [flow.docs.graphs :as graphs]
            [flow.front.editor.utils :refer [search-graphs]]
            [flow.front.editor.views.utils :refer [adjustable-textbox]]))

(defn graph-ports-view [{:keys [placeholder description]} port-type ports new-port-fn]
  (let [new-port-name (atom "")]
    (fn []
      [:div.sideview-wrapper
       [:div.description description]
       (for [port @ports]
         [:div.item
          [:span.name (:name port)
           (if (:bound port)
             (str " -> " (get-in port [:bound port-type])))
           [:a.action
            {:on-mouse-down #(do (dispatch [:request-switch
                                            [:binding]
                                            {:port-name (:name port)
                                             :port-type port-type
                                             :mouse-x (.-clientX %)
                                             :mouse-y (.-clientY %)}])
                                 (.stopPropagation %)
                                 (.preventDefault %))
             :on-context-menu #(.preventDefault %)}
            [:i.fa.fa-exchange]]]])
       [:div.new-port
        [adjustable-textbox
         {:class "uk-form dark-textbox"
          :value @new-port-name
          :placeholder placeholder
          :on-change #(reset! new-port-name (-> % .-target .-value))}]
        [:button.uk-button.add-port
         {:type "button"
          :on-click #(do (new-port-fn @new-port-name)
                         (reset! new-port-name ""))}
         [:i.fa.fa-plus]]]])))

(defn graph-instances-view []
  (let [instances (subscribe [:instances])]
    (fn []
      [:div.graph-instances
       [:div.sideview-wrapper
        [:div.description "Instances"]
        [:div.items
         (doall (for [[i instance] (map-indexed vector @instances)]
                  [:div.item
                   [:span.name
                    [:a
                     {:on-click #(do (dispatch [:switch-to-instance instance]))}
                     (str i)]]]))]]])))

(defn graph-inports-view []
  (let [inports (subscribe [:inports])]
    (fn []
      [:div.graph-inports
       [graph-ports-view {:placeholder nil
                          :description "Inports"}
        :inport
        inports #(dispatch [:update-graph (fn [x] (graphs/update-inport x {:name %}))])]])))

(defn graph-outports-view []
  (let [outports (subscribe [:outports])]
    (fn []
      [:div.graph-outports
       [graph-ports-view {:placeholder nil
                          :description "Outports"}
        :outport
        outports #(dispatch [:update-graph (fn [x] (graphs/update-outport x {:name %}))])]])))


(defn name-textbox-view []
  (let [name (subscribe [:name])
        state (subscribe [:state-for [:editing-name]])]
    (fn []
      (if @state
        [:div.friendly-name
         [adjustable-textbox
          {:class "uk-form friendly-name-textbox"
           :on-change #(dispatch-sync [:emit-event-only-for [:editing-name]
                                       :on-change {:value (-> % .-target .-value)}])
           :value (:value @state)}]
         [:button.uk-button.friendly-name-save
          {:type "button"
           :on-click #(dispatch [:request-finish-only-for [:editing-name]
                                 {:save? true}])} [:i.fa.fa-floppy-o]]]
        [:div.friendly-name
         [:div.friendly-name-const @name]
         [:button.uk-button.friendly-name-edit
          {:type "button"
           :on-click #(dispatch [:request-switch [:editing-name]])} [:i.fa.fa-pencil-square-o]]
         [:button.uk-button.new-subgraph
          {:type "button"
           :on-click #(dispatch [:new-subgraph])} [:i.fa.fa-asterisk]]]))))

(defn search-new-node-view []
  (let [options (atom {})
        value (atom "")]
    (fn []
      [:div.new-instance
       [:input {:list "valid-names"
                :type "text"
                :class "new-instance-textbox"
                :placeholder "New Instance"
                :value @value
                :on-change
                #(do (reset! value (-> % .-target .-value))
                     (search-graphs
                      {:query {:name {:$regex @value}}
                       :handler (fn [graphs]
                                  (reset! options graphs))}))}]
       [:button.uk-button.new-instance-add
        {:type "button"
         :on-click #(if-let [graph (first (filter
                                             (fn [x] (= (:name x) @value))
                                             @options))]
                      (do (println graph)
                          (dispatch [:update-graph
                                     (fn [x]
                                       (graphs/update-node x {:id (generate-id)
                                                              :type (:id graph)}))])
                          (reset! options {})
                          (reset! value "")))}
        [:i.fa.fa-plus]]
       [:datalist {:id "valid-names"}
        (doall (for [option @options]
                 ^{:key (str "suggestion-" (:id option))}
                 [:option {:value (:name option)}]))]])))
