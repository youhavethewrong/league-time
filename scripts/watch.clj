(require '[cljs.build.api :as b])

(b/watch "src"
  {:main 'league-time.core
   :output-to "out/league_time.js"
   :output-dir "out"})
