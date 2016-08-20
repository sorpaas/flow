(set-env!
  ; Test path can be included here as source-files are not included in JAR
  ; Just be careful to not AOT them
  :source-paths #{"src/cljs" "src/less" "src/scss" "test/clj" "test/cljs"}
  :resource-paths #{"src/clj" "src/cljc" "src/assets"}
  :dependencies '[[org.clojure/clojure "1.8.0"]
                  [org.clojure/clojurescript "1.9.36"]
                  [org.clojure/core.async "0.2.374"]
                  [org.clojure/tools.cli "0.3.3"]
                  [org.clojure/tools.namespace "0.2.11"]
                  [org.clojure/tools.nrepl "0.2.12"]
                  [org.clojure/core.match "0.3.0-alpha4"]
                  [org.clojure/data.json "0.2.6"]
                  [prismatic/schema "1.0.5"]
                  [com.taoensso/truss "1.1.1"]
                  [com.rpl/specter "0.9.2"]
                  [com.taoensso/timbre "4.3.1"]
                  [com.taoensso/sente "1.8.1"]
                  [mount "0.1.10"]

                  [boot/core              "2.5.5"      :scope "test"]
                  [adzerk/boot-cljs       "1.7.228-1"  :scope "test"]
                  [adzerk/boot-cljs-repl  "0.3.0"      :scope "test"]
                  [crisptrutski/boot-cljs-test "0.3.0-SNAPSHOT" :scope "test"]
                  [com.cemerick/piggieback "0.2.1"     :scope "test"]
                  [weasel                 "0.7.0"      :scope "test"]
                  [org.clojure/tools.nrepl "0.2.12"    :scope "test"]
                  [adzerk/boot-reload     "0.4.5"      :scope "test"]
                  [adzerk/boot-test       "1.0.7"      :scope "test"]
                  [deraen/boot-less       "0.5.0"      :scope "test"]
                  ;; For boot-less
                  [org.slf4j/slf4j-nop    "1.7.13"     :scope "test"]
                  [deraen/boot-ctn        "0.1.0"      :scope "test"]
                  [danielsz/boot-shell    "0.0.1"      :scope "test"]

                  ; Backend
                  [http-kit "2.1.19"]
                  [buddy "0.10.0"]
                  [metosin/ring-http-response "0.6.5"]
                  [ring-middleware-format "0.7.0"]
                  [prismatic/om-tools "0.4.0"]
                  [prismatic/plumbing "0.5.2"]
                  [prismatic/schema "1.0.4"]
                  [ring "1.4.0"]
                  [compojure "1.4.0"]
                  [hiccup "1.0.5"]
                  [clojurewerkz/neocons "3.1.0"]
                  [com.novemberain/monger "3.0.2"]
                  [clj-http "2.1.0"]
                  [clj-oauth "1.5.5"]
                  [robert/hooke "1.3.0"]

                  ; Frontend
                  [reagent "0.6.0-alpha2"]
                  [cljs-ajax "0.5.5"]
                  [secretary "1.2.3"]
                  [re-frame "0.7.0"]

                  ; LESS
                  [org.webjars/bootstrap "3.3.6"]])

(require
  '[adzerk.boot-cljs      :refer [cljs]]
  '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl repl-env]]
  '[adzerk.boot-reload    :refer [reload]]
  '[adzerk.boot-test      :refer [test]]
  '[deraen.boot-less      :refer [less]]
  '[deraen.boot-ctn       :refer [init-ctn!]]
  '[crisptrutski.boot-cljs-test :refer [test-cljs prep-cljs-tests run-cljs-tests]]
  '[danielsz.boot-shell :refer [shell]]
  '[clojure.tools.namespace.repl :refer [set-refresh-dirs]])

; Watch boot temp dirs
(init-ctn!)

(task-options!
  pom {:project 'flow
       :version "0.1.0-SNAPSHOT"
       :description "Flow-based personal assistant."}
  aot {:namespace #{'flow.core}}
  jar {:main 'flow.core}
  cljs {:source-map true}
  less {:source-map true})

(deftask dev
  "Start the dev env..."
  [s speak           bool "Notify when build is done"
   p port       PORT int  "Port for web server"
   a use-sass        bool "Use Scss instead of less"
   t test-cljs       bool "Compile and run cljs tests"]
  (apply set-refresh-dirs (get-env :directories))
  (require 'dev)
  (comp
   (watch)
   (less)
   (reload :on-jsload 'js.reload/reload!)
   ;; This starts a repl server with piggieback middleware
   (cljs-repl)
   (cljs)
   (if speak (boot.task.built-in/speak) identity)))

(deftask run-tests []
  (comp
   (test)
   (test-cljs :namespaces #{"flow.front.core-test"})))

(deftask autotest []
  (comp
   (watch)
   (run-tests)))

(deftask package
  "Build the package"
  []
  (comp
   (less :compression true)
   (cljs :optimizations :advanced)
   (aot)
   (pom)
   (uber)
   (jar)
   (target)))

(deftask deploy
  "Deploy to the remote server"
  []
  (comp
   (package)
   (shell :script "nixops deploy -d flowed-web")))
