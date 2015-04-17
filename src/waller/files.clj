(ns waller.files
  (:require [clojure.string :as cstr]
            [clojure.java.io :as io]
            [waller.core :as core]
            [clojure.edn :as cedn])
  (:import java.net.URI))

(def ^:private default-dir "migrations")

(def migration-pattern
  #"(.*)\.(up|down)\.edn$")

(defn file-name [file]
  (.getName (io/file file)))

(defn- migration? 
  "Figures out if a file should be included in the migration set.
   Taken directly from ragtime.sql.files"
  [file]
  (re-find migration-pattern (file-name file)))

(defn- migration-id 
  "Extracts the id from the migration files
   Taken directly from ragtime.sql.files."
  [file]
  (second (re-find migration-pattern (file-name file))))

(defn- assert-migrations-complete 
  "Makes sure that the migration exists in pairs of up and down.
   Taken from ragtime.sql.files"
  [migration-files]
  (let [incomplete-files (remove #(= (count (val %)) 2)
                                 migration-files)]
    (assert (empty? incomplete-files)            
            (str "Incomplete migrations found. "
                 "Please provide up and down migration files for "
                 (cstr/join ", " (keys incomplete-files))))))

;; -------- Picking the right reaction ----------------

(defn create? [val]
  (= :create val))

(defn delete? [val]
  (= :drop val))

(defn args? [m num]
  (= num (count m)))

(defn create-database [{:keys [db action] :as m}]
  (when (and db (create? action) (args? m 2))
    :create-database))

(defn create-collection [{:keys [db action collection-name] :as m}]
  (when (and db (create? action) collection-name (args? m 3))
    :create-collection))

(defn drop-table [{:keys [db action] :as m}]
  (when (and db (delete? action) (args? m 2))
    :drop-database))

(defn drop-collection [{:keys [db action collection-name] :as m}]
  (when (and db (delete? action) (args? m 2) collection-name)
    :drop-collection))

(def reactors (juxt 
                    create-database
                    create-collection
                    drop-table
                    drop-collection))

(defn pick-reaction [m db]
  (let [reactions (remove nil? (apply reactors m))]
    (assert (= 1 (count reactions)))
    (first reactions)))
  

(defmulti react pick-reaction)

(defmethod react :create-database [edn db] 
  (println "creating database" db)
  )

(defmethod react :create-collection [edn db] 
  (println "creating collection"))

(defmethod react :drop-database [edn db]
  (println "droping database"))

(defmethod react :drop-collection [edn db]
  (println "dropping collection" ))
  
(defmethod react :default [m db]
  (println "no impl" m))

;; --------------- End Picking the Right Reaction ---------

(defn- modifier-action 
  "Modifies ArangoDB based upon the edn provided. The function returned takes 
   the connection created during the connection function invocation."
  [mod-file]
  (let [edn (cedn/read-string (slurp (mod-file)))]
    (fn [db]
      (react edn db))))

(defn- make-migration[[id [up down]]]
  {:id id, :up (modifier-action up), :down (modifier-action down)})


(defn- get-migration-files 
  "Reads through directory's children to see if there are any .edn files.
   Returns them grouped by id with the upgrade first in the list downgrade
   second."
  [dir]
  (let [files (->> (.listFiles (io/file dir))
                (filter migration?)
                (sort)
                (group-by migration-id))]
    (assert-migrations-complete files)
    files))

(defn migrations
  "Returns a list of migrations to apply. It assumes that the 
  waller.core.connection fn was called first (implicit flow from Ragtime?) If
  you want to create a migration seperately, you can use the [conn dir] or 
  [conn] instances instead."
  ([] (migrations default-dir))
  ([conn] (migrations conn default-dir))
  ([conn dir]
    (->> 
      (get-migration-files dir)
      (map make-migration)
      (sort-by :id))))

