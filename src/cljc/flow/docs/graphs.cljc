(ns flow.docs.graphs
  (:require [schema.core :as s]
            [flow.utils :refer [generate-id]]
            [com.rpl.specter :refer [transform ALL select FIRST]]))

;;; Definitions of types

(def GraphId s/Str)
(def NodeId s/Str)

(def Node
  {:id NodeId
   :type GraphId
   (s/optional-key :display) {:position {:x s/Int :y s/Int}}})

(def Connection
  {:from {:node NodeId :outport s/Str}
   :to {:node NodeId :inport s/Str}})

(def PortHeader
  {:name s/Str})

(def PrimitivePort
  (merge PortHeader
         {}))

(def NormalInport
  (merge PortHeader
         {(s/optional-key :bound) {:node NodeId
                                   :inport s/Str}}))

(def NormalOutport
  (merge PortHeader
         {(s/optional-key :bound) {:node NodeId
                                   :outport s/Str}}))

(def GraphHeader
  {:id GraphId})

(def PrimitiveGraph
  (merge GraphHeader
         {:name s/Str
          :inports [PrimitivePort]
          :outports [PrimitivePort]
          :primitive (s/eq true)
          :worker s/Str}))

(def NormalGraph
  (merge GraphHeader
         {(s/optional-key :name) s/Str
          :inports [NormalInport]
          :outports [NormalOutport]
          :primitive (s/eq false)
          :nodes [Node]
          :connections [Connection]}))

(def Graph
  (s/conditional
   #(:primitive %)
   PrimitiveGraph

   #(not (:primitive %))
   NormalGraph))

;;; Protocols

(defprotocol GraphCollection
  (find-graph-by-id [db id])
  (find-graph-by-name [db name])
  (-commit-graph! [db graph]))

;;; Useful functions

(defn find-node-by-id [graph id]
  (first (filter #(= (:id %) id) (:nodes graph))))

(defn find-inport-by-name [graph name]
  (first (filter #(= (:name %) name) (:inports graph))))

(defn find-outport-by-name [graph name]
  (first (filter #(= (:name %) name) (:outports graph))))

(defn find-inports-by-bound [graph bound]
  (cond
    (= bound :all) (:inports graph)
    (nil? bound) (filter #(nil? (:bound %)) (:inports graph))

    :else
    (filter #(and (or (= (:node bound) :all) (= (get-in % [:bound :node]) (:node bound)))
                  (or (= (:inport bound) :all) (= (get-in % [:bound :inport]) (:inport bound)))) (:inports graph))))

(defn find-inport-by-bound [graph bound]
  (first (find-inports-by-bound graph bound)))

(defn find-outports-by-bound [graph bound]
  (cond
    (= bound :all) (:outports graph)
    (nil? bound) (filter #(nil? (:bound %)) (:outports graph))

    :else
    (filter #(and (or (= (:node bound) :all) (= (get-in % [:bound :node]) (:node bound)))
                  (or (= (:outport bound) :all) (= (get-in % [:bound :outport]) (:outport bound)))) (:outports graph))))

(defn find-outport-by-bound [graph bound]
  (first (find-outports-by-bound graph bound)))

(defn find-unbounded-inports [graph]
  (find-inports-by-bound graph nil))

(defn find-unbounded-outports [graph]
  (find-outports-by-bound graph nil))

(defn find-connections [graph from to]
  (let [from (if (= from :all) {:node :all :outport :all} from)
        to (if (= to :all) {:node :all :inport :all} to)]
    (filter #(and (or (= (:node from) :all) (= (get-in % [:from :node]) (:node from)))
                  (or (= (:outport from) :all) (= (get-in % [:from :outport]) (:outport from)))
                  (or (= (:node to) :all) (= (get-in % [:to :node]) (:node to)))
                  (or (= (:inport to) :all) (= (get-in % [:to :inport]) (:inport to))))
            (:connections graph))))

(defn find-connection [graph from to]
  (first (find-connections graph from to)))

;;; Validation

(defn- wrap-exception [try-fn]
  (try (try-fn)
       (catch #?(:clj Exception
                 :cljs :default) e
         false)))

(defn validate-node! [db graph node]
  (s/validate Node node)
  (assert (some? (find-graph-by-id db (:type node))))
  true)

(defn validate-node [db graph node] (wrap-exception (validate-node! db graph node)))

(defn validate-connection! [db graph connection]
  (let [from-node (find-node-by-id graph (get-in connection [:from :node]))
        from-graph (find-graph-by-id db (:type from-node))
        to-node (find-node-by-id graph (get-in connection [:to :node]))
        to-graph (find-graph-by-id db (:type to-node))]
    (validate-node! db graph from-node)
    (validate-node! db graph to-node)
    (assert (some? (find-outport-by-name from-graph (get-in connection [:from :outport]))))
    (assert (some? (find-inport-by-name to-graph (get-in connection [:to :inport])))))
  true)

(defn validate-connection [db graph connection] (wrap-exception (validate-connection! db graph connection)))

(defn validate-graph! [db graph]
  (s/validate Graph graph)
  (if (not (:primitive graph))
    (do (doseq [node (:nodes graph)]
          (validate-node! db graph node))
        (doseq [connection (:connections graph)]
          (validate-connection! db graph connection))))
  true)

(defn validate-graph [db graph] (wrap-exception (validate-graph! db graph)))

;;; Database related functions

(defn commit-graph! [db graph]
  (validate-graph! db graph)
  (-commit-graph! db graph))

;;; Utils

(defn new-normal-graph []
  {:id (generate-id)
   :primitive false
   :inports []
   :outports []
   :nodes []
   :connections []})

(defn relevant-graphs [db graph]
  (let [graph (if (string? graph) (find-graph-by-id db graph) graph)]
    (let [ids (map :type (:nodes graph))]
      (filter #(not (= % graph))
              (distinct (map #(find-graph-by-id db %) ids))))))

(defn wrap-relevants [db element]
  (let [element (if (string? element) (find-graph-by-id db element) element)]
    {:target element
     :relevants (relevant-graphs db element)}))

;;; Operations

(defn update-node [graph node]
  (if (find-node-by-id graph (:id node))
    (transform [:nodes ALL #(= (:id %) (:id node))] (fn [_] node) graph)
    (update-in graph [:nodes] conj node)))

(defn update-connection [graph connection]
  (if (find-connection graph (:from connection) (:to connection))
    (transform [:connections ALL #(and (= (:from %) (:from connection))
                                       (= (:to %) (:to connection)))] (fn [_] connection) graph)
    (update-in graph [:connections] conj connection)))

(defn update-inport [graph inport]
  (if (find-inport-by-name graph (:name inport))
    (transform [:inports ALL #(= (:name %) (:name inport))] (fn [_] inport) graph)
    (update-in graph [:inports] conj inport)))

(defn update-outport [graph outport]
  (if (find-outport-by-name graph (:name outport))
    (transform [:outports ALL #(= (:name %) (:name outport))] (fn [_] outport) graph)
    (update-in graph [:outports] conj outport)))

;;; Deletion

;;; TODO Inports and outports deletion may affact other graphs. It is not
;;; handled here.

;; (defn delete-inport-by-name [graph name]
;;   (if-let [inport (find-inport-by-name graph name)]
;;     (assoc graph :inports
;;            (into [] (filter #(not (= inport %)) (:inports graph))))
;;     graph))

;; (defn delete-outport-by-name [graph name]
;;   (if-let [outport (find-outport-by-name graph name)]
;;     (assoc graph :outports
;;            (into [] (filter #(not (= outport %)) (:outports graph))))
;;     graph))

;; (defn delete-inports-by-mapped-to [graph id]
;;   (if-let [inport (find-inport-by-mapped-to graph id)]
;;     (assoc graph :inports
;;            (into [] (filter #(not (= inport %)) (:inports graph))))
;;     graph))

;; (defn delete-outports-by-mapped-to [graph id]
;;   (if-let [outport (find-outport-by-mapped-to graph id)]
;;     (assoc graph :outports
;;            (into [] (filter #(not (= outport %)) (:outports graph))))
;;     graph))

;;; Deletion of connections and nodes

(defn delete-connections [graph from to]
  (if-let [connections (find-connections graph from to)]
    (assoc graph :connections
           (into [] (filter (fn [connection]
                              (not (some #(= % connection) connections))) (:connections graph))))
    graph))

(defn delete-node [graph node]
  (if-let [node (find-node-by-id graph (if (string? node) node (:id node)))]
    (-> graph
        (assoc :nodes
               (into [] (filter #(not (= (:id %) (:id node))) (:nodes graph))))
        (assoc :inports
               (into [] (map #(if (= (get-in % [:bound :node]) (:id node)) (dissoc % :bound) %) (:inports node))))
        (assoc :outports
               (into [] (map #(if (= (get-in % [:bound :node]) (:id node)) (dissoc % :bound) %) (:outports node))))
        (delete-connections :all {:node (:id node) :inport :all})
        (delete-connections {:node (:id node) :outport :all} :all))
    graph))
