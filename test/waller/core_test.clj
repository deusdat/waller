(ns waller.core-test
  (:require [clojure.test :refer :all]
            [waller.core :refer :all]
            [ragtime.core :as rag]))

(def ^:private open-no-credentials "http://localhost:8529")
(def ^:private open-credentials "http://tester:pass1@localhost:8529")
(def ^:private secure-no-credentials "https://localhost:8529")
(def ^:private secure-credentials "https://tester2:pass2@localhost:8529")


(deftest connection-creation
  (testing 
    "Should create a connection for the proper http url"
    (is (= (->ArangoDatabase {:type :simple, :url "http://localhost:8529"})
           (rag/connection open-no-credentials))))
  (testing 
    "Should create a connection for the proper https url"
    (is (= (->ArangoDatabase {:type :simple, :url "https://localhost:8529"})
           (rag/connection secure-no-credentials))))
  (testing 
    "Should create a connection for the proper http url with credentials"
    (is (= (->ArangoDatabase {:type :simple, :url "http://localhost:8529",
                              :username "tester", :password "pass1"})
           (rag/connection open-credentials))))
  (testing 
    "Should create a connection for the proper https url with credentials"
    (is (= (->ArangoDatabase {:type :simple, :url "https://localhost:8529",
                              :username "tester2", :password "pass2"})
           (rag/connection secure-credentials)))))