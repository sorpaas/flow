(ns flow.front.editor.views.utils
  (:require [reagent.core :as reagent :refer [atom]]
            [re-frame.core :refer [dispatch dispatch-sync subscribe]]))

(defn adjust-textbox-width [textbox]
  (set! (-> textbox .-style .-width) 0)
  (set! (-> textbox .-style .-width)
        (str (Math/max (-> textbox .-scrollWidth) 20) "px")))

(defn adjustable-textbox [{:keys [style class on-change after-change value placeholder]}]
  (reagent/create-class
   {:reagent-render
    (fn [{:keys [style on-change value]}]
      [:input
       {:style style
        :class class
        :placeholder placeholder
        :type "text" :value value
        :on-change #(do (if on-change (on-change %))
                        (adjust-textbox-width (-> % .-target))
                        (if after-change (after-change %)))}])
    :component-did-mount #(adjust-textbox-width (reagent/dom-node %))
    :component-did-update #(adjust-textbox-width (reagent/dom-node %))}))
