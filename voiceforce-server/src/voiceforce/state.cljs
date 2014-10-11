(ns voiceforce.state
  (:require [voiceforce.util :refer [trace log debug]]
            [cljs.nodejs :as nodejs]
            [cljs.core.async :as async :refer [<! >! take! put! chan timeout close!]]
            [cljs.core.match])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]
                   [cljs.core.match.macros :refer [match]]))


;; {
;;   "msg_id" : "fcca6b96-5bcc-4f9b-aecd-1001c10cb787",
;;   "_text" : "driving to Uber",
;;   "outcomes" : [ {
;;     "_text" : "driving to Uber",
;;     "intent" : "drive_to",
;;     "entities" : {
;;       "account" : [ {
;;         "value" : "Uber"
;;       } ]
;;     },
;;     "confidence" : 0.505
;;   } ]
;; }

;; op-id 006o0000004ny83
;; edna-id 003o000000BTNrm
;; event-id 00Uo0000001bbsMEAQ
;; event-relation-id 0REo0000000IbkiGAC

(defn drive-to
  [entities state]
  (go (let [account (-> entities :account first :value)
            op (<! (sf/name->opportunity account "Account"))
            op-id (:Id op)
            update (<! (sf/get-latest-update op-id))
            update (or update " Last price is $10M, updated last week by John")]
        (debug "drive-to " entities state)
        {:state {:op op-id}
         :text (str "Good luck! Here is the latest update about this opportunity: " update)})))

(defn who-attend
  [entities state]
  (go
    (let [op-id (:op state)
          attendees (<! (sf/opportunity->attendees op-id))
          attendees-string (->> attendees
                                (map (fn [{:keys [Name Title] :as x}]
                                       (println x)
                                       (str Name ", " Title)))
                                (string/join " and "))]
      (debug "who-attend " entities state)
      {:state {:op op-id
               :attendees attendees}
       :text attendees})))

;; TODO how can I ask about missing name entity?
(defn tell-more
  [entities state]
  (go (if-let [name (-> entities :name first :value)]
        (let [cid (->> state :attendees
                       (filter #(re-find (js/RegExp. name) (:Name %)))
                       first
                       :Id)
              desc (-> (sf/more-details cid)
                       <!
                       trace
                       :Description)]
          {:state (merge state {:user cid})
           :text desc}))))


(defn handle
  [x]
  (let [{wit-res :wit state :state} x
        _ (trace state)
        state (-> state js/JSON.parse js->clj :keywordize-keys true)
        text (:_text wit-res)
        outcome (-> wit-res :outcomes first)
        intent (-> outcome :intent)
        entities (-> outcome :entities)
        _ (debug "Text received from devices " text " for outcome : " (pr-str outcome))
        {text :text
         state :state} (match [intent]

                              ["drive_to"]
                              (<! (drive-to entities state))

                              ["who_attend"]
                              (<! (who-attend entities state))

                              ["tell_more"]
                              (<! (tell-more entities state))

                              :else
                              {:state state
                               :text "Sorry, I didn't get that"})]
    {:state (-> state clj->js js/JSON.stringify)
    :text text}))
