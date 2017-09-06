(defproject league-time "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.908"]]

  :profiles {:dev
             {:dependencies [[com.cemerick/piggieback "0.2.2"]
                             [org.clojure/tools.nrepl "0.2.13"]]
              :plugins [[lein-cljsbuild "1.1.7"]]
              :source-paths ["src" "dev"]
              :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}}

  :clean-targets [[:cljsbuild :builds 0 :compiler :output-to]
                  :target-path
                  :compile-path]

  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src"]
                :compiler {:output-dir "out"
                           :output-to "league-time.js"
                           :optimizations :simple
                           :target :nodejs}}]})
