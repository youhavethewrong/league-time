(ns user
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]))

(defn readit
  [f]
  (let [b (slurp (io/file f))
        eee (edn/read-string b)]
    eee))

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
            match (match-id (:matches detail))]
        (println time " - " (:name detail) " - " (:name match))))))

(defn date-range
  [{a-start :startDate} {b-start :startDate}]
  (compare a-start b-start))

(defn time-range
  [{a-time :scheduledTime} {b-time :scheduledTime}]
  (compare a-time b-time))

(defn upcoming-matches
  [schedule-data]
  (let [tournies (filter #(:published %) (:highlanderTournaments schedule-data))
        sorted-tournies (into (sorted-set-by date-range) tournies)
        {:keys [id description startDate endDate] :as tournament-info} (last sorted-tournies)
        schedule (:scheduleItems schedule-data)
        tournament-schedule (filter #(= id (:tournament %)) schedule)
        sorted-t-sched (into (sorted-set-by time-range) tournament-schedule)]
    (doseq [m sorted-t-sched]
      (let [{:keys [match bracket scheduledTime]} m
            b (keyword bracket)
            i (keyword match)
            n (get-in tournament-info [:brackets b :name])]
        (when n
          (println scheduledTime
                   n
                   (get-in tournament-info [:brackets b :matches i :name])
                   "best of"
                   (get-in tournament-info [:brackets b :matchType :options :best_of])
                   (get-in tournament-info [:brackets b :matches i :state])))
        ))
    ))

(comment
  (let [w (readit "out.edn")]
    (upcoming-matches w)
    )
)
