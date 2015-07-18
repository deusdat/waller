(ns waller.ragtime
  "This ns implements the features required by Ragtime."
  (:require [ragtime.protocols :as p]
            [waller.core :refer [create-context,ensure-track-store!]]
            [travesedo.document :as tdoc]
            [clojure.edn :as edn]
            [clojure.string :as cstr]
            [clojure.java.io :as io]
            [resauce.core :as resauce]
            [waller.files :refer [react]]
            [ragtime.core :as rag])
  (:import [java.io File]))


(def migration-col "waller")
(defn migration-url [migration-col] (str "/_api/document/" migration-col "/"))

;; Allows the interaction between Ragtime and previously applied migrations.
(defrecord ArangoDatabase
  [conn db]
  p/DataStore
  (add-migration-id [this id]
    (ensure-track-store! this migration-col)
    (tdoc/create (merge this {:in-collection migration-col, 
                              :payload {:_key id, :id id}})))
  
  (remove-migration-id [this id]
    (ensure-track-store! this  migration-col)
    (tdoc/delete (assoc this :_id (str migration-col "/" id))))
  
  (applied-migration-ids [this]
    (ensure-track-store! this  migration-col)
    (let [id-ctx (assoc this :in-collection migration-col :type :key),
          ids (:documents (tdoc/read-all-docs id-ctx)),
          sorted-ids (map #(cstr/replace %  (migration-url migration-col) "") 
                          (sort ids))]
      sorted-ids)))

(defn arango-connection
  "Constructs an instance of the ArangoDatabase by converting the 'url' into 
   a travesedo context."
  [url]
  (map->ArangoDatabase (create-context url)))

(defrecord ArangoMigration
  [id up down]
  p/Migration
  (id [_] id)
  (run-up!   [_ db] (react up db))
  (run-down! [_ db] (react down db)))

(defn arango-migration 
  "Converts untyped map into Ragtime Migration"
  [migration-map]
  (map->ArangoMigration migration-map))

;; Shameless stealing from Ragtime JDBC to load files.
(let [pattern (re-pattern (str "([^" File/separator "]*)" File/separator "?$"))]
  (defn- basename [file]
    (second (re-find pattern (str file)))))


(defn- remove-extension [file]
  (second (re-matches #"(.*)\.[^.]*" (str file))))

(defn- file-extension [file]
  (re-find #"\.[^.]*$" (str file)))

(defmulti load-files
  "Given an collection of files with the same extension, return a ordered
  collection of migrations. Dispatches on extension (e.g. \".edn\"). Extend
  this multimethod to support new formats for specifying SQL migrations."
  (fn [files] (file-extension (first files))))

(defmethod load-files ".edn" [files]
  (for [file files]
    (-> (slurp file)
      (edn/read-string)
      (update-in [:id] #(or % (-> file basename remove-extension)))
      (arango-migration))))

(defn- load-all-files [files]
  (->> (sort-by str files)
    (group-by file-extension)
    (vals)
    (mapcat load-files)))

(defn load-directory
  "Load a collection of Ragtime migrations from a directory."
  [path]
  (load-all-files (file-seq (io/file path))))

(defn load-resources
  "Load a collection of Ragtime migrations from a classpath prefix."
  [path]
  (load-all-files (resauce/resource-dir path)))

;; Migration functions
(defn migrate-from-classpath
  [{:keys [url index dir], :or {index [], dir "migrations"}}]
    (rag/migrate-all (arango-connection url)
                     index
                     (load-resources dir)))

(defn create-config-for-repl 
  "Helper constructor for the Ragtime configuration. This will return a 
   map with keys :datastore of type DataStore and :migrations a list of 
   Migration."
  ([url]
    (create-config-for-repl url "migrations"))
  ([url classpath-dir]
    {:datastore (arango-connection url),
     :migrations (load-resources classpath-dir)}))