(ns league-time.core
  (:require [cljs.nodejs :as node]
            [clojure.pprint :as pprint]
            [clojure.string :refer [blank? starts-with? upper-case]]))

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

(defn extract-events
  [data start-date end-date]
  (let [parsed-start (.parse js/Date start-date)
        parsed-end (.parse js/Date end-date)]
    (->> data
         (filter
          (fn item-in-date-range [{time :scheduledTime :as item}]
            (let [parsed-date (.parse js/Date time)]
              (and (<= parsed-date parsed-end)
                   (>= parsed-date parsed-start)))))
         (map
          (fn format-event [{time :scheduledTime
                             {:keys [blockLabel blockPrefix subBlockLabel subBlockPrefix]} :tags}]
            {:time time
             :event (str blockPrefix (when-not (blank? blockPrefix) " ")
                         blockLabel (when-not (blank? blockLabel) " ")
                         subBlockPrefix (when-not (blank? subBlockPrefix) " ")
                         subBlockLabel )})))))

(defn time-<
  [{time-string-a :time} {time-string-b :time}]
  (let [parsed-a (.parse js/Date time-string-a)
        parsed-b (.parse js/Date time-string-b)]
    (compare parsed-a parsed-b)))

(defn upcoming-matches
  "Digs out the season, start date, and end date from a response to a
   GET on the lolesports/scheduleItems API endpoint."
  [data]
  (let [tournies (filter #(:published %) (:highlanderTournaments data))
        {:keys [description startDate endDate] :as current-tourney} (last tournies)
        events (extract-events (:scheduleItems data) startDate endDate)
        sorted-events (into (sorted-set-by time-<) events)]
    (println (str "The current season is " description ".  It begins " startDate " and ends " endDate "."))
    (pprint/pprint sorted-events)))

(defn write-data
  [data filename]
  (println "Saving response to" filename)
  (.writeFileSync fs filename (pprint/write data :pretty true :stream nil))
  data)

(defn parse-json
  [response]
  (let [data (js->clj (.parse js/JSON response) :keywordize-keys true)]
;;    (write-data data "out.edn")
    data))

(defn parse-response
  [url]
  (-> (js/Promise.
       (fn [resolve reject]
         (let [scheme (if (starts-with? url "https") https http)]
           (.get scheme url
                 (fn [res]
                   (if (not= (aget res "statusCode") 200)
                     (do
                       (println "Rejecting due to bad status code" (aget res "statusCode"))
                       (reject res))
                     (let [body (atom "")]
                       (-> res
                           (.on "data" #(swap! body str (.toString %)))
                           (.on "end" (fn [_] (resolve @body)))))))))))
      (.then #(upcoming-matches (parse-json %)))
      (.catch js/console.error)))

;; cljs
(defn -main
  [& args]
  (if (empty? args)
    (println "Provide a league to check:" (keys leagues))
    (if-let [league-url (get-league (upper-case (first args)))]
      (parse-response league-url))))

(set! *main-cli-fn* -main)

;; lumo
(comment
  (if (empty? *command-line-args*)
    (println "Provide a league to check:" (keys leagues))
    (if-let [league-url (get-league (upper-case (first *command-line-args*)))]
      (parse-response league-url))))
