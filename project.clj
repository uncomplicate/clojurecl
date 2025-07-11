;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(defproject uncomplicate/clojurecl "0.16.1-SNAPSHOT"
  :description "ClojureCL is a Clojure library for parallel computations with OpenCL."
  :url "https://github.com/uncomplicate/clojurecl"
  :scm {:name "git"
        :url "https://github.com/uncomplicate/clojurecl"}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.11.3"]
                 [org.jocl/jocl "2.0.5"]
                 [org.clojure/core.async "1.6.741"]
                 [uncomplicate/commons "0.15.0"]
                 [uncomplicate/fluokitten "0.10.0"]]

  :codox {:metadata {:doc/formt a:markdown}
          :src-dir-uri "http://github.com/uncomplicate/clojurecl/blob/master/"
          :src-linenum-anchor-prefix "L"
          :output-path "docs/codox"
          :namespaces [uncomplicate.clojurecl.core
                       uncomplicate.clojurecl.info
                       uncomplicate.clojurecl.toolbox
                       uncomplicate.clojurecl.internal.protocols
                       uncomplicate.clojurecl.internal.constants
                       uncomplicate.clojurecl.internal.utils]}

  :profiles {:dev {:plugins [[lein-midje "3.2.1"]
                             [lein-codox "0.10.8"]]
                   :global-vars {*warn-on-reflection* true
                                 *assert* true
                                 *unchecked-math* :warn-on-boxed
                                 *print-length* 128}
                   :dependencies [[midje "1.10.10"]]
                   :jvm-opts ^:replace ["-Dclojure.compiler.direct-linking=true"
                                        "--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED"
                                        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"]}}

  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]

  :source-paths ["src/clojure" "src/opencl"]
  :test-paths ["test/clojure" "test/opencl"]
  :java-source-paths ["src/java"])
