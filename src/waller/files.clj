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

(defn execute-aql-maker [{:keys [action fn] :as m}]
  (when (and (modify? action) fn (args? m 2))
    :execute-aql-maker))

(defn create-graph [{:keys [action graph] :as m}]
  (when (and (create? action) graph (args? m 2))
    :create-graph))

(defn drop-graph [{:keys [action graph] :as m}]
  (when (and (delete? action) graph (args? m 2))
    :drop-graph))

(defn create-hash-index [{:keys [action index collection-name fields] :as m}]
  (when (and (create? action) (= :hash index) collection-name fields)
    :create-hash-index))

(defn create-fulltext-index [{:keys [action index collection-name fields] :as m}]
  (when (and (create? action) (= :text index) collection-name fields)
    :create-fulltext-index))

(defn create-geo-index [{:keys [action index collection-name fields] :as m}]
  (when (and (create? action) (= :geo index) collection-name fields)
    :create-geo-index))

(defn create-skiplist-index [{:keys [action index collection-name fields] :as m}]
  (when (and (create? action) (= :skip index) collection-name fields)
    :create-skiplist-index))

(defn create-cap-index [{:keys [action index collection-name fields] :as m}]
  (when (and (create? action) (= :cap index) collection-name fields)
    :create-cap-index))

(def reactors (juxt 
                    execute-aql-maker
                    create-database
                    create-collection
                    create-graph
                    drop-graph
                    drop-table
                    drop-collection
                    execute-aql
                    ;; Added 7/25/2015
                    create-cap-index
                    create-hash-index
                    create-fulltext-index
                    create-geo-index
                    create-skiplist-index))

(defn pick-reaction [edn db]
  (let [reactions (remove nil? (reactors edn))]
    (assert (= 1 (count reactions)) 
            (str "Non-nil reactions " (print-str reactions)))
    (first reactions)))
  

(defmulti react pick-reaction)

(defmethod react :create-hash-index [edn db]
  (println "Attempting to create hash index for " (:collection-name edn))
  (assert (core/success? (tidx/create-hash! db 
                                            (:collection-name edn)
                                            (:fields edn)
                                            (or (:unique edn) false)))))

(defmethod react :create-geo-index [edn db]
  (println "Attempting to create geo index for " (:collection-name edn))
  (assert (core/success? (tidx/create-geo! db 
                                            (:collection-name edn)
                                            (:fields edn)))))

(defmethod react :create-cap-index [edn db]
  (println "Attempting to create cap index for " (:collection-name edn))
  (assert (core/success? (tidx/create-cap-constraint! db 
                                           (:collection-name edn)
                                           (select-keys edn [:size :byte-size])))))

(defmethod react :create-fulltext-index [edn db]
  (println "Attempting to create fulltext index for " (:collection-name edn))
  (assert (core/success? (apply tidx/create-fulltext! 
                                (remove nil? [db 
                                              (:collection-name edn)
                                              (:fields edn)
                                              (:min-length edn)])))))

(defmethod react :create-skiplist-index [edn db]
  (println "Attempting to create skiplist index for " (:collection-name edn))
  (assert (core/success? (tidx/create-skiplist! db 
                                            (:collection-name edn)
                                            (:fields edn)
                                            (or (:unique edn) false)
                                            (or (:sparse edn) false)))))

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

(defn ppass [o]
  (println o)
  o)

(defmethod react :execute-aql-maker [edn db]
  (println "Attempting to run fn " edn)
  (let [f (:fn edn)]
    (assert (symbol? f) ":fn must be a symbol")
    (if-let [aql-maker (resolve f)]
      (doseq [query (flatten (conj [] (aql-maker)))]
        (assert (core/success? (tqry/aql-query 
                                 (assoc db :payload {:query (ppass query)})))))
      (throw (Exception.
                    (str "Cannot find the fn you want to migrate " f))))))

(defmethod react :create-index [edn db]
  )
;; --------------- End Picking the Right Reaction ---------


