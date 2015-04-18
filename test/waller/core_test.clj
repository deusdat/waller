(ns waller.core-test
  (:require [clojure.test :refer :all]
            [waller.core :refer :all]
            [ragtime.core :as rag]))

(def ^:private open-no-credentials "arango://localhost:8529/test-db")
(def ^:private open-credentials "arango://tester:pass1@localhost:8529/test-db")
(def ^:private secure-no-credentials "arangos://localhost:8529/test-db")
(def ^:private secure-credentials 
  "arangos://tester2:pass2@localhost:8529/test-db")


(deftest connection-creation
  (testing 
    "Should create a connection for the proper http url"
    (is (= (->ArangoDatabase {:type :simple, :url "http://localhost:8529"} 
             "test-db")
           (rag/connection open-no-credentials))))
  (testing 
    "Should create a connection for the proper https url"
    (is (= (->ArangoDatabase {:type :simple, :url "https://localhost:8529"}
             "test-db")
           (rag/connection secure-no-credentials))))
  (testing 
    "Should create a connection for the proper http url with credentials"
    (is (= (->ArangoDatabase {:type :simple, :url "http://localhost:8529",
                              :username "tester", :password "pass1"}
             "test-db")
           (rag/connection open-credentials))))
  (testing 
    "Should create a connection for the proper https url with credentials"
    (is (= (->ArangoDatabase {:type :simple, :url "https://localhost:8529",
                              :username "tester2", :password "pass2"}
             "test-db")
           (rag/connection secure-credentials)))))