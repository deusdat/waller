(ns waller.core
  (:require [travesedo.database :as tdb]
            [travesedo.collection :as tcol]
            [clojure.string :as cstr]))

(def ^:private base-conn {:type :simple})

(def ^:private migration-db "waller-migrator")

(defn create-db-users 
  "Creates a map with key of :payload with a value of {:name, :users []} if the
   connection has credentials."
  [conn]
  (if-let [username (:uname conn)]
    (if-let [password (:password conn)]
      {:users [{:uname username, :password password}]})))

(defn create-db! [conn]
  (let [req  (assoc conn 
                    :payload (merge {:name (:db conn)} (create-db-users conn)))]
    (tdb/create req)))

(defn success? [res]
  (< 199 (:code res) 300))

(defn ensure-tracking-database! 
  [conn]
  (let [res (tdb/get-database-info conn)]
    (if (success? res)
      :success
      (let [cret (create-db! conn)] 
        (assert (success? cret) (str "Couldn't create migration db "  cret))
        :success))))

(defn ensure-tracking-collection!
  [conn migration-col]
  (let [base-conn {:conn conn}
        res (tcol/get-collection-info (assoc conn
                                             :collection migration-col))]
    (if (success? res) 
      :success 
      (tcol/create (assoc conn
                          :payload {:name migration-col})))))

(defn ensure-track-store!
  "Makes sure that the database and table for migrations is stored"
  [conn migration-col]
  (let [success (ensure-tracking-database! conn)]
    (assert (= success :success))
    (ensure-tracking-collection! conn migration-col)))


(defn find-credentials 
  "Finds the credentials associated with the url, or an empty map if none"
  [uri]
  (when-let [creds (.getUserInfo uri)]
    (zipmap [:uname :password] (cstr/split creds #":"))))

(defn find-url [uri]
  (let [src-port (.getPort uri)
        scheme (case (.getScheme uri) "arangos" "https" "arango" "http")
        port (if (> src-port -1) (str ":" src-port) "")
        host (.getHost uri)]
    {:url (str scheme "://" host port)}))

(defn find-db [uri]
  (subs (.getPath uri) 1))

(defn create-context [url]
  (let [uri (java.net.URI. url)]
    (assoc {:db (find-db uri)} 
           :conn (merge base-conn (find-url uri) (find-credentials uri)))))