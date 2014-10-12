(ns voiceforce.state
  (:require [voiceforce.util :refer [trace log debug]]
            [voiceforce.sfdc :as sf]
            [clojure.string :as string]
            [cljs.nodejs :as nodejs]
            [cljs.core.async :as async :refer [<! >! take! put! chan timeout close!]]
            [cljs.core.match])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]
                   [cljs.core.match.macros :refer [match]]))

(def util (js/require "util"))


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
;; willy-id 005o00000010d6YAAQ

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
                                (string/join " and "))
          cnt (count attendees)]
      (debug "who-attend " entities state)
      {:state {:op op-id
               :attendees attendees}
       :text (str cnt
                  " "
                  (if (= 1 cnt)
                    "person is"
                    "people are")
                  " attending: " attendees-string)})))

;; TODO how can I ask about missing name entity?
(defn tell-more
  [entities state]
  (go (if-let [name (-> entities :name first :value)]
        (let [cid (or (->> state
                           :attendees
                           (filter #(re-find (js/RegExp. name) (:Name %)))
                           first
                           :Id)
                      (->> (sf/search name)
                           <!
                           (filter (comp (partial = "Contact") :type :attributes))
                           first
                           :Id))
              desc (-> (sf/more-details cid)
                       <!
                       trace
                       :Description)]
          {:state (merge state {:user cid})
           :text desc})
        (println "tell-more: no name"))))

(defn news
  [entities state]
  (go (let [account (-> entities :account first :value)]
        {:state {:account account}
         :text "That sounds great!"})))

(defn update-opportunity
  [entities state]
  (go (let [account (or (:account state) (-> entities :account first :value))
            op (-> (sf/name->opportunity account "Account")
                   <!)
            op-id (:Id op)
            op-name (:Name op)
            amount (-> entities :amount_of_money first :value)]
        (<! (sf/update-opportunity-size op-id amount))
        {:state (merge state {:op op-id :op-name op-name :amount amount})
         :text "Done"})))

(defn inform
  [entities state]
  (go (let [name (-> entities :name first :value)
            account (-> state :account)
            amount (-> state :amount)
            op-name (-> state :op-name)
            msg (.format util "Update on %s: opportunity %s proposition changed to %s dollars."
                         account op-name amount)
            text (.format util "OK. I let %s know." name)]
        (<! (sf/chatter-send name msg))
        {:state state
         :text text})))

(def days ["Sunday" "Monday" "Tuesday" "Wednesday" "Thursday"
           "Friday" "Saturday"])
(defn pretty-date
  "Friday 30"
  [iso-d]
  (let [d (-> iso-d js/Date.)]
    (str (get days (.getDay d)) " " (.getDate d))))

(defn task
  [entities state]
  (go (let [action (-> entities :reminder first :value)
            datetime (-> entities :datetime first :value)
            text (str "Reminder set for " (pretty-date datetime))
            wly "005o00000010d6YAAQ"]
        (<! (sf/set-task wly datetime action))
        {:state state
         :text text})))

(defn handle
  [x]
  (go (let [{intent :intent entities :entities text :text state :state} x
         state (-> state js/JSON.parse (js->clj :keywordize-keys true))
         _ (debug "Text received from devices " text " for intent : " intent " entities " entities)
         {text :text
          state :state} (match [intent]

          ["drive_to"]
          (<! (drive-to entities state))

          ["who_attend"]
          (<! (who-attend entities state))

          ["tell_more"]
          (<! (tell-more entities state))

          ["news"]
          (<! (news entities state))

          ["update_opportunity"]
          (<! (update-opportunity entities state))

          ["inform"]
          (<! (inform entities state))

          ["task"]
          (<! (task entities state))

          :else
          {:state state
           :text "Sorry, I didn't get that"})]
     {:state (-> state clj->js js/JSON.stringify)
      :text text})))
