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

;; -----------------------------------------------------------------------------
;; Accessors

(defn e->account [entities]
  (-> entities :account first :value))

(def s->account :account)

(def s->op :op)
(def s->op-name :op-name)

;; -----------------------------------------------------------------------------
;; Intents

(defn drive-to
  [entities state]
  (go (if-let [account (or (e->account entities) (s->account state))]
        (let [op (<! (sf/name->opportunity account "Account"))
              op-id (:Id op)]
          (if-let [update (and op-id (<! (sf/get-latest-update op-id)))]
            (let [text (str "Good luck! Here is the latest update about this opportunity: " update)]
              {:state (merge state {:op op-id :account account})
               :text text})
            {:state (merge state {:op op-id :account account})
             :text  "Good luck! Nothing new happened."}))
        {:state state :text "That sounds good."})))

(defn who-attend
  [entities state]
  (go (let [account (e->account entities)
            state-op (s->op state)
            state-account (s->account state)]
        (if-let [op-id (cond
                        account
                        (-> (sf/name->opportunity account "Account")
                            <! :Id)

                        state-account
                        (-> (sf/name->opportunity state-account "Account")
                            <! :Id)

                        :else
                        nil)]
          (let [attendees (<! (sf/opportunity->attendees op-id))]
            (cond (= :no-meeting attendees)
                  {:state {:op op-id}
                   :text (str "There is no meeting scheduled with "
                              (or account state-account))}
                  :else
                  (let [attendees-string (->> attendees
                                              (map (fn [{:keys [Name Title] :as x}]
                                                     (println x)
                                                     (str Name ", " Title)))
                                              (string/join " and "))
                        cnt (count attendees)
                        peeps (if (= 1 cnt) "person is" "people are")]
                    {:state (merge state {:op op-id :attendees attendees})
                     :text (str cnt " " peeps " attending: " attendees-string)})))
          {:state state :text (if account
                                "I don't know what meeting you're interested in."
                                (str "I don't know what the " account " meeting is."))}))))

(defn tell-more
  [entities state]
  (go (if-let [name (-> entities :name first :value)]
        (let [cid (or (and name
                           (->> state
                                :attendees
                                (filter not-empty)
                                (filter #(re-find (js/RegExp. name) (:Name %)))
                                first
                                :Id))
                      (and name
                           (->> (sf/search name)
                                <!
                                (filter (comp (partial = "Contact") :type :attributes))
                                first
                                :Id))
                      (->> state
                           :attendees
                           first
                           :Id))
              desc (-> (sf/more-details cid)
                       <!
                       trace
                       :Description)]
          (if desc
            {:state (merge state {:contact cid}) :text (str "Sure. " desc)}
            {:state (merge state {:contact cid})
             :text (str "I don't have any records on " (or name "this person."))}))
        {:state state :text "Excuse me, what contact are you interested in?"})))

(defn news
  [entities state]
  (go (if-let [account (-> entities :account first :value)]
        (let [op (<! (sf/name->opportunity account "Account"))
              op-id (or (:Id op) (s->op state))
              op-name (or (:Name op) (s->op-name state))]
          (if (and op-id op-name)
            {:state (merge state {:account account :op-name op-name :op op-id})
             :text (str "That sounds great! Shall I update the " op-name " opportunity?")}
            {:state (merge state {:account account})
             :text (str "That sounds great! I know " account " is a crucial account.")}))
        {:state state :text "I'm glad to hear it."})))

(defn update-opportunity
  [entities state]
  (go (if-let [amount (-> entities :amount_of_money first :value)]
        (let [account (e->account entities)
              state-op (s->op state)
              state-op-name (s->op-name state)
              state-account (s->account state)]
          (if-let [op (cond
                       account
                       (-> (sf/name->opportunity account "Account")
                           <!)

                       (and state-op state-op-name)
                       {:Id state-op :Name state-op-name}

                       state-account
                       (-> (sf/name->opportunity state-account "Account")
                           <!)

                       :else
                       nil)]
            (let [{op-name :Name op-id :Id} op]
              (<! (sf/update-opportunity-size op-id amount))
              {:state (merge state {:op op-id :op-name op-name :amount amount})
               :text (str "Ok. I updated the " op-name " opportunity to " amount)})
            {:state state :text "Excuse me, what opportunity are you referring to?"}))
        {:state state :text "Excuse me, how do you want to update the opportunity?"})))

(defn inform
  [entities state]
  (go (if-let [name (-> entities :name first :value)]
        (let [account (-> state :account)
              amount (-> state :amount)
              op-name (s->op-name state)
              msg (.format util "Update on %s: opportunity %s proposition changed to %s dollars."
                           (or account op-name) op-name amount)
              text (.format util "OK. I let %s know." name)]
          (<! (sf/chatter-send name msg))
          {:state state :text text})
        {:state state :text "I'm sorry, could you repeat?"})))

(def days ["Sunday" "Monday" "Tuesday" "Wednesday" "Thursday" "Friday" "Saturday"])
(defn pretty-date
  "Friday 30"
  [iso-d]
  (when iso-d
    (let [d (-> iso-d js/Date.)
          date (.getDate d)
          date-str (case date
                     (1 21 31) "st"
                     (2 22) "nd"
                     (3 23) "rd"
                     "th")]
      (str (get days (.getDay d)) " the " date date-str))))

(defn task
  [entities state]
  (go (let [action (-> entities :reminder first :value)
            datetime (-> entities :datetime first :value)
            datetime (if-let [x (:from datetime)]
                       x
                       datetime)
            wly "005o00000010d6YAAQ"]
        (if (and action datetime)
          (let [text (str "Reminder set for " (pretty-date datetime))]
            (<! (sf/set-task wly datetime action))
            {:state state :text text})
          {:state state :text "Excuse me, what would you like to remind you?"}))))

(defn create-meeting [entities state]
  (go (let [contact-name (-> entities :contact first :value)
            datetime (-> entities :datetime first :value)
            datetime (if-let [x (:from datetime)]
                       x
                       datetime)
            account-name (or (e->account entities)
                             (s->account state))
            text (str "Meeting with " contact-name " scheduled on " (pretty-date datetime))]
        (<! (sf/create-meeting datetime contact-name account-name))
        {:state state :text text})))

(defn handle
  [x]
  (go (trace
       (let [{intent :intent entities :entities text :text state :state} x
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

                              ["create_meeting"]
                              (<! (create-meeting entities state))

                              :else
                              {:state state
                               :text "Sorry, I didn't get that"})]
         {:state (-> state clj->js js/JSON.stringify)
          :text text}))))
