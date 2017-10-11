(ns league-time.core
  (:require [cljs.nodejs :as node]
            [clojure.pprint :as pprint]
            [clojure.string :refer [blank? starts-with? upper-case]]
            ["moment/moment" :as moment]))

(node/enable-util-print!)
(.on js/process "uncaughtException" #(js/console.error %))

(defonce http (node/require "http"))
(defonce https (node/require "https"))
(defonce fs (node/require "fs"))

(def useragent "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.1 (KHTML, like Gecko) Chrome/22.0.1207.1 Safari/537.1")

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

(defn interpret-data
  [url f]
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
      (.then f)
      (.catch js/console.error)))

(def leagues
  {"NA" 2
   "EU" 3
   "NA-CS" 4
   "EU-CS" 5
   "LCK" 6
   "LPL" 7
   "LMS" 8
   "WORLDS" 9
   "MSI" 10})

(defn get-league-schedule
  [league]
  (if (contains? (set (keys leagues)) league)
    (str "http://api.lolesports.com/api/v1/scheduleItems?leagueId=" (leagues league))
    (println (str "Can't get schedule for " league ". Valid leagues are " (keys leagues) "."))))

(defn date-range
  [{a-start :startDate} {b-start :startDate}]
  (compare a-start b-start))

(defn time-range
  [{a-time :scheduledTime} {b-time :scheduledTime}]
  (compare a-time b-time))

(defn team-scores
  [scores rosters]
  (apply str
         (interpose
          ":"
          (map
           (fn [[k v]]
             (str (get-in rosters [k :name]) " " v))
           scores))))

(defn position-score
  [result rosters]
  (if-let [winner (get-in rosters [(keyword (:roster (ffirst result))) :name])]
    (str "[" winner ":W," (get-in rosters [(keyword (:roster (first (second result)))) :name]) ":L]")
    "No result"))

(defn upcoming-matches
  "Digs out the season, start date, and end date from a response to a
   GET on the lolesports/scheduleItems API endpoint."
  [data league-name]
  (let [tournies (filter #(:published %) (:highlanderTournaments data))
        sorted-tournies (into (sorted-set-by date-range) tournies)
        {:keys [id description startDate endDate] :as tournament-info} (last sorted-tournies)
        tournament-schedule (filter #(= id (:tournament %)) (:scheduleItems data))
        sorted-t-sched (into (sorted-set-by time-range) tournament-schedule)]
    (println (str "The current season is " description ".  It begins " startDate " and ends " endDate "."))
    (doseq [scheduled-match sorted-t-sched]
      (let [{:keys [match bracket scheduledTime]} scheduled-match
            b (keyword bracket)
            i (keyword match)
            n (get-in tournament-info [:brackets b :name])
            the-scores (team-scores (get-in tournament-info [:brackets b :matches i :scores])
                                    (:rosters tournament-info))
            formatted-time (.format (moment. scheduledTime) "YYYY.MM.DD HH:mm:ss Z")]
        (when n
          (println formatted-time
                   n
                   (get-in tournament-info [:brackets b :matches i :name])
                   (str "(best of " (get-in tournament-info [:brackets b :matchType :options :best_of]) ")")
                   (if (not (empty? the-scores))
                     the-scores
                     (position-score
                      (get-in tournament-info [:brackets b :matches i :standings :result])
                      (:rosters tournament-info)))))))))

(defn -main
  [& args]
  (if (empty? args)
    (println "Provide a league to check:" (keys leagues))
    (let [league-name (upper-case (first args))]
      (if-let [league-schedule-url (get-league-schedule league-name)]
        (interpret-data league-schedule-url #(upcoming-matches (parse-json %) league-name))))))

(set! *main-cli-fn* -main)
