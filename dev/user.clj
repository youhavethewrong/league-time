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

(comment
    ;; tournament-info
    (:description :leagueReference :platformIds :roles :startDate :gameIds :title :endDate :id :leagueId :liveMatches :rosteringStrategy :rosters :brackets :breakpoints :league :queues :published)

    ;; scores
    (:name :groupName :roles :matchType :state :gameMode :matches :standings :canManufacture :id :bracketType :position :scoring :scores :inheritableMatchScoringStrategy :input :matchScores :groupPosition)

    ;; matches
    (:name :roles :remadeGames :matchType :state :id :position :scores :input :groupPosition :games)

    ;; weird matches
    (:name :roles :remadeGames :matchType :state :standings :id :position :scores :input :groupPosition :games)
    ;; wm -> standings
    (:result :timestamp :source :note :history :closed)
  )

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
    (str winner " Win")
    "No result"))

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
            n (get-in tournament-info [:brackets b :name])
            the-scores (team-scores (get-in tournament-info [:brackets b :matches i :scores])
                                    (:rosters tournament-info))]

        (when n
          (println scheduledTime
                   n
                   (get-in tournament-info [:brackets b :matches i :name])
                   "best of"
                   (get-in tournament-info [:brackets b :matchType :options :best_of])
                   (get-in tournament-info [:brackets b :matches i :state])
                   (if (not (empty? the-scores))
                     the-scores
                     (position-score
                      (get-in tournament-info [:brackets b :matches i :standings :result])
                      (:rosters tournament-info)))))
        ))
    ))

(comment
  (let [w (readit "out.edn")]
    (upcoming-matches w)
    )
)
