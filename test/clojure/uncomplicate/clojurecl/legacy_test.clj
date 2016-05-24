(ns uncomplicate.clojurecl.legacy-test
  (:require [uncomplicate.clojurecl
             [core :refer [*context* *command-queue*]]
             [legacy :refer :all]]
            [midje.sweet :refer :all]))

(with-default-1
  (facts "Legacy bindings"
         *context* => truthy
         *command-queue* => truthy))
