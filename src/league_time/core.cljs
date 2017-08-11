(ns league-time.core
  (:require [cljs.nodejs :as node]
            [clojure.pprint :as pprint]
            [clojure.string :refer [blank? starts-with? upper-case]]))

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

(defn time-<
  [{time-string-a :time} {time-string-b :time}]
  (let [parsed-a (.parse js/Date time-string-a)
        parsed-b (.parse js/Date time-string-b)]
    (compare parsed-a parsed-b)))

(def leagues
  {"NA" 2
   "EU" 3
   "NA-CS" 4
   "EU-CS" 5
   "LCK" 6
   "LMS" 7
   "LPL" 8
   "WORLDS" 9
   "MSI" 10})

(defn get-league
  [league]
  (if (contains? (set (keys leagues)) league)
    (str "http://api.lolesports.com/api/v1/leagues/" (leagues league))
    (println (str "Can't get league info about " league ".  Valid leagues are " (keys leagues) "."))))

(defn get-league-schedule
  [league]
  (if (contains? (set (keys leagues)) league)
    (str "http://api.lolesports.com/api/v1/scheduleItems?leagueId=" (leagues league))
    (println (str "Can't get schedule for " league ". Valid leagues are " (keys leagues) "."))))

(defn extract-matches
  "Allow a day for timezone fun."
  [data start-date end-date]
  (let [parsed-start (.parse js/Date start-date)
        parsed-end (.parse js/Date end-date)
        parsed-start (- parsed-start 86400000 )
        parsed-end (+ parsed-end 86400000)]
    (->> data
         (filter
          (fn item-in-date-range [{time :scheduledTime :as item}]
            (let [parsed-date (.parse js/Date time)]
              (and (<= parsed-date parsed-end)
                   (>= parsed-date parsed-start)))))
         (map
          (fn format-match [{time :scheduledTime
                             match :match
                             bracket :bracket
                             tournament :tournament}]
            {:time time
             :match match
             :bracket bracket
             :tournament tournament})))))

(defn detail-matches
  [detail-data sorted-matches]
  (let [tournament-id (:tournament (first sorted-matches))
        matching-tourneys (filter
                           #(= tournament-id (:id %))
                           (:highlanderTournaments detail-data))
        current-tourney (first matching-tourneys)
        brackets (:brackets current-tourney)]
    (doseq [{match :match bracket :bracket time :time} sorted-matches]
      (let [match-id (keyword match)
            bracket-id (keyword bracket)
            detail (bracket-id brackets)
            match (match-id (:matches detail))
            formatted-time (.toLocaleString (js/Date. time) "en-US" {"timeZone" "America/Chicago"})]
        (println formatted-time " - " (:name detail) " - " (:name match))))))

(defn upcoming-matches
  "Digs out the season, start date, and end date from a response to a
   GET on the lolesports/scheduleItems API endpoint."
  [data league-name]
  (let [tournies (filter #(:published %) (:highlanderTournaments data))
        {:keys [description startDate endDate] :as current-tourney} (last tournies)
        matches (extract-matches (:scheduleItems data) startDate endDate)
        sorted-matches (into (sorted-set-by time-<) matches)
        league-detail-url (get-league league-name)]
    (println (str "The current season is " description ".  It begins " startDate " and ends " endDate "."))
    (interpret-data league-detail-url #(detail-matches (parse-json %) sorted-matches))))

;; cljs
(defn -main
  [& args]
  (if (empty? args)
    (println "Provide a league to check:" (keys leagues))
    (let [league-name (upper-case (first args))]
      (if-let [league-schedule-url (get-league-schedule league-name)]
        (interpret-data league-schedule-url #(upcoming-matches (parse-json %) league-name))))))

(set! *main-cli-fn* -main)
