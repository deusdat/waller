(ns waller.files
  (:require [clojure.string :as cstr]
            [clojure.java.io :as io]
            [waller.core :as core]
            [travesedo.database :as tdb]
            [travesedo.collection :as tcol]
            [travesedo.query :as tqry]
            [travesedo.graph :as tgraph]
            [travesedo.index :as tidx])
  
  (:import java.net.URI))

(def ^:private default-dir "migrations")

(def migration-pattern
  #"(.*)\.edn$")

(defn file-name [file]
  (.getName (io/file file)))

(defn- migration? 
  "Figures out if a file should be included in the migration set.
   Taken directly from ragtime.sql.files"
  [file]
  (re-find migration-pattern (file-name file)))

(defn migration-id 
  "Extracts the id from the migration files
   Taken directly from ragtime.sql.files."
  [file]
  (second (re-find migration-pattern (file-name file))))

;; -------- Picking the right reaction ----------------

(defn create? [val]
  (= :create val))

(defn delete? [val]
  (= :drop val))

(defn modify? [val]
  (= :modify val))

(defn args? [m num]
  (= num (count m)))

(defn create-database [{:keys [action] :as m}]
  (when (and (create? action) (args? m 1))
    :create-database))

(defn create-collection [{:keys [action collection-name] :as m}]
  (when (and (create? action) collection-name (args? m 2))
    :create-collection))

(defn drop-table [{:keys [action] :as m}]
  (when (and (delete? action) (args? m 1))
    :drop-database))

(defn drop-collection [{:keys [action collection-name] :as m}]
  (when (and (delete? action) collection-name (args? m 2))
    :drop-collection))

(defn execute-aql [{:keys [action aql] :as m}]
  (when (and (modify? action) aql (args? m 2))
    :execute-aql))

(defn create-index [{:keys [action index collection-name] :as m}]
  (when (and (create? action) index collection-name)
    :create-index))

(defn create-graph [{:keys [action graph] :as m}]
  (when (and (create? action) graph (args? m 2))
    :create-graph))

(defn drop-graph [{:keys [action graph] :as m}]
  (when (and (delete? action) graph (args? m 2))
    :drop-graph))

(def reactors (juxt 
                    create-database
                    create-collection
                    create-graph
                    drop-graph
                    drop-table
                    drop-collection
                    execute-aql
                    create-index))

(defn pick-reaction [m db]
  (let [reactions (remove nil? (reactors m))]
    (assert (= 1 (count reactions)) (str "Non-nil reactions " 
                                      (print-str reactions)))
    (first reactions)))
  

(defmulti react pick-reaction)

(defmethod react :create-database [edn db] 
  (println "Attempting to create database: " (:db edn))
  (let [req (assoc db
             :payload (merge {:name (:db edn)} 
                             (core/create-db-users db)))]
  (tdb/create req)))

(defmethod react :create-graph [edn db]
  (println "Attempting to create graph: " (get-in edn [:graph :name]))
  (let [ctx (merge db {:payload (:graph edn)})
        new-graph (tgraph/create-graph! ctx)]
  (if (core/success? new-graph)
    :success
    (throw (Exception. (str "Could not create graph from " edn))))))

(defmethod react :create-collection [edn db] 
  (println "Creating a collection " edn)
  (if (core/success? (tcol/create (assoc db 
                                    :payload {:name (:collection-name edn)})))
    :success
    (throw (Exception. (str "Could not create collection from " edn)))))

(defmethod react :drop-graph [edn db] 
  (if (core/success? (tgraph/delete-graph! (merge db edn)))
    :success
    (throw (Exception. (str "Could not create collection from " edn)))))

(defmethod react :drop-database [edn db]
  (assert (core/success? (tdb/drop db))))

(defmethod react :drop-collection [edn db]
  (println "dropping collection" )
  (assert (core/success? (tcol/delete-collection 
                 (assoc db :collection (:collection-name edn))))))

(defmethod react :execute-aql [edn db]
  (assert (core/success? (tqry/aql-query (assoc db 
                                           :payload {:query (:aql edn)})))))

(defmethod react :create-index [edn db]
  )
;; --------------- End Picking the Right Reaction ---------


