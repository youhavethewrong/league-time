(ns league-time.core
  (:require [cljs.nodejs :as node]
            [clojure.string :refer [starts-with? upper-case]]
            ;; [lumo.core :refer [*command-line-args*]]
            ))

(node/enable-util-print!)
(.on js/process "uncaughtException" #(js/console.error %))

(defonce http (node/require "http"))
(defonce https (node/require "https"))
(defonce fs (node/require "fs"))

(def leagues
  {"NA" 2
   "EU" 3
   "LCK" 5
   "LMS" 6
   "LPL" 7})

(defn get-league
  [league]
  (if (contains? (set (keys leagues)) league)
    (str "http://api.lolesports.com/api/v1/scheduleItems?leagueId=" (leagues league))
    (println (str "No league " league " in " (keys leagues) "."))))

(def useragent "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.1 (KHTML, like Gecko) Chrome/22.0.1207.1 Safari/537.1")

(defn upcoming-matches
  "Digs out the season, start date, and end date from a response to a
   GET on the lolesports/scheduleItems API endpoint."
  [data]
  (let [tournies (filter #(:published %) (:highlanderTournaments data))
        {:keys [description startDate endDate] :as current-tourney} (last tournies)]
    (println (str "The current season is " description ".  It begins " startDate " and ends " endDate "."))))

(defn parse-json
  [response]
  (js->clj (.parse js/JSON response) :keywordize-keys true))

(defn parse-response
  [url]
  (-> (js/Promise.
       (fn [resolve reject]
         (let [scheme (if (starts-with? url "https") https http)]
           (.get scheme url
                 (fn [res]
                   (let [body (atom "")]
                     (-> res
                         (.on "data" #(swap! body str (.toString %)))
                         (.on "end" (fn [_] (resolve @body))))))))))
      (.then #(upcoming-matches (parse-json %)))
      (.catch js/console.error)))

;; TODO
(defmacro main-with-args-sym
  [sym]
  `(if (empty? ~sym)
     (println "Provide a league to check:" (keys leagues))
     (if-let [league-url (get-league (upper-case (first ~sym)))]
       (parse-response league-url))))

;; cljs
(defn -main [& args]
  (if (empty? args)
    (println "Provide a league to check:" (keys leagues))
    (if-let [league-url (get-league (upper-case (first args)))]
      (parse-response league-url))))

(set! *main-cli-fn* -main)

(comment
  ;; lumo
  (main-with-args-sym *command-line-args*)
  )
