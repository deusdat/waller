(ns waller.files-test
  (:require [clojure.test :refer :all]
            [waller.files :refer :all])
  (:import java.net.URI))

(def url-with-credentials "http://tester:pass1@localhost:8529")
(def url-without-credentials "http://localhost:8529")

(def uri-with-credentials (java.net.URI. url-with-credentials))
(def uri-without-credentials (java.net.URL. url-without-credentials))

(deftest credential-detection-and-parsing
  (testing 
    "url-without-credentials"
    (is (nil? (find-credentials uri-without-credentials))))
  
  (testing 
    "url-with-credentials"
    (is (= {:username "tester", :password "pass1"}
          (find-credentials uri-with-credentials)))))

(deftest connection-creation
  (testing 
    "Create connection with credentials"
    (is (= {:type :simple, :url "http://localhost:8529", 
            :username "tester", :password "pass1"}
           (create-context url-with-credentials)))))