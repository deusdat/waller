(ns waller.integration-test
  (:require 
    [waller.core :refer :all]
    [waller.files :refer :all]
    [ragtime.main :as rmain]
    [clojure.test :refer :all]
    [travesedo.database :as tdb]
    [travesedo.collection :as tcol]))

(def arango-db "arangodb:8529")

(def ctx {:conn {:type :simple, 
                 :url (str "http://" arango-db)},
          :db  "testing-db", 
          :wait-for-sync true})


(defn load-collections []
  (:collections (tcol/get-all-collections ctx)))

(defn has-col-by-name [col-name colls]
  (is (boolean (filter #(= (:name %) col-name) colls))))                             

(defn has-profile? [colls]
  (has-col-by-name "profile" colls))

(defn has-migration? [colls]
  (has-col-by-name "migration" colls))

(defn has-other-col? [colls]
  (has-col-by-name "recipes" colls))

(defn has-testing-aql? [colls]
  (has-col-by-name "testing-aql" colls))

(defn has-vertex1? [colls]
  (has-col-by-name "vertex1" colls))

(defn has-correct-num-docs? []
  (is (= 1000 (:count (tcol/load-collection (assoc ctx :collection :testing-aql))))))

(deftest full-migration
  (tdb/drop (assoc ctx :db "testing-db" ))
  (testing "Try migrating a database."
    (rmain/migrate {:database (str "arango://tester1:pass@" 
                                arango-db "/testing-db")
                    :migrations "waller.files/migrations"})
    (let [colls (load-collections)]
      (has-profile? colls)
      (has-migration? colls)
      (has-other-col? colls)
      (has-testing-aql? colls)
      (has-correct-num-docs?)
      (has-vertex1? colls))))