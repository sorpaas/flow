(ns flow.pages
  (:require [hiccup.core :refer [html]]
            [hiccup.page :refer [html5 include-js include-css]]
            [flow.docs.graphs :as graphs]
            [flow.utils :refer [include-data]]))

(defn index-header-links [user]
  (if user
    [:div.header-links
     [:a.signup {:href "/graphs/home"} "Your Graph"]
     [:a {:href "/logout"} "Logout"]]
    [:div.header-links
     [:a {:href "/login"} "Login"]
     [:a.signup {:href "/signup"} "Sign Up"]]))

(defn index-page [user]
  (html
   (html5
    [:head
     [:title "Flow"]
     [:meta {:charset "utf-8"}]
     [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
     (include-css "css/common.css")]
    [:body
     [:div.uk-container.uk-container-center.uk-margin-large-top.uk-margin-large-bottom
      [:header
       (index-header-links user)]
      [:footer
       [:div.footer-links
        [:a {:href "/"} "Home"] " | "
        [:a {:href "/graphs/home"} "Your Graph"]]]]])))

(def login-page
  (html
   (html5
    [:head
     [:title "Login - Flow"]
     [:meta {:charset "utf-8"}]
     [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
     (include-css "css/common.css")]
    [:body
     [:div.uk-container.uk-container-center.uk-margin-large-top.uk-margin-large-bottom
      [:header
       [:div.header-links
        [:a.signup {:href "/signup"} "Sign Up"]]]
      [:h1 "Login"]
      [:form.login {:method "post"}
       [:label "Username"]
       [:input.text {:type "text"
                     :placeholder "Username"
                     :name "username"}]
       [:label "Password"]
       [:input.text {:type "password"
                     :placeholder "Password"
                     :name "password"}]
       [:input.submit {:type "submit"
                       :value "Login"}]]
      [:footer
       [:div.footer-links
        [:a {:href "/"} "Home"] " | "
        [:a {:href "/elements"} "Elements"]]]]])))

(def signup-page
  (html
   (html5
    [:head
     [:title "Sign Up"]
     [:meta {:charset "utf-8"}]
     [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
     (include-css "css/common.css")]
    [:body
     [:div.uk-container.uk-container-center.uk-margin-large-top.uk-margin-large-bottom
      [:header
       [:div.header-links
        [:a.signup {:href "/login"} "Login"]]]
      [:h1 "Sign Up"]
      [:form.login {:method "post"}
       [:label "Username"]
       [:input.text {:type "text"
                     :placeholder "Username"
                     :name "username"}]
       [:label "Password"]
       [:input.text {:type "password"
                     :placeholder "Password"
                     :name "password"}]
       [:label "Password Confirmation"]
       [:input.text {:type "password"
                     :placeholder "Password Confirmation"
                     :name "password-confirmation"}]
       [:input.submit {:type "submit"
                       :value "Sign Up"}]]
      [:footer
       [:div.footer-links
        [:a {:href "/"} "Home"] " | "
        [:a {:href "/elements"} "Elements"]]]]])))

(defn graph-page [db js]
  (html
   (html5
    [:head
     [:title "Graph - Flowed"]
     [:meta {:charset "utf-8"}]
     [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
     (include-css "/css/editor.css")]
    [:body
     [:div#app]
     (include-js js)])))
