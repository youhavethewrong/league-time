(require '[lumo.build.api :as b])

(b/build "src"
         {:main 'league-time.core
          :output-to "main.js"
          :optimizations :advanced
          :target :nodejs})
