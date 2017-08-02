(require 'cljs.build.api)

(cljs.build.api/build "src"
                      {:main 'league-time.core
                       :output-to "main.js"
                       :target :nodejs})
