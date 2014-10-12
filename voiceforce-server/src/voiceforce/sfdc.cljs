(ns voiceforce.sfdc
  (:require [cljs.core.async :as async :refer [<! >! take! put! chan timeout close!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]))

(def request (js/require "request"))
(def cons-key "3MVG9xOCXq4ID1uGUZEDkWbuTVfqOqP3IWRgPCy.sxHJ1edmupOjoYnEnuM1KrGWPF3QBa3yYEYHvz5lu038y")
(def cons-secret "8065890795745227552")
(def secret-token "rC7fjvarT8AOYemUI68abKEQ")
(def access-token "00Do0000000ciyx!ARQAQC48oZL9PuxKbGwH3TAlbL497ckLadU_kH9r8R1UMkpIoxBU9LKBhjjpAPiBqnau2BRwzZccQC7iufnNX6Ok5LHZcYwf")

(defn sf-url [x]
  (str "https://na17.salesforce.com/services/data/v32.0" x))

(defn trace [x]
  (println "TRACE" x)
  x)

(defn req [meth url & [opts]]
  (let [out (chan)
        opts' (merge-with
               merge
               {:method meth
                :url  (sf-url url)
                :json true
                :headers {:Authorization (str "Bearer " access-token)}
                }
               opts)]
    (println "req" meth url opts')
    (request. (clj->js opts')
              (fn [err res body]
                (println err (.-statusCode res) body)
                (cond
                 err
                 (js/console.log (str "ERROR sending req to salesforce: " err))

                 (and res (< (.-statusCode res) 300))
                 (let [body (js->clj body :keywordize-keys true)]
                   (when body (put! out body)))
                 :else
                 (js/console.log
                  (str "ERROR sending req to salesforce (http " (.-statusCode res) ")")
                  (js->clj (.toString res) :keywordize-keys true)))))
    out))

(defn search
  "executes a full-text search"
  [x]
  (let [q (str "FIND {" x "}
                IN ALL FIELDS
                RETURNING Account (Id, Name),
                          Contact (Id, Name),
                          User (Id, Name)")]
    (req "GET" "/search" {:qs {:q q}})))

(defn query
  "executes a SOQL query"
  [q]
  (req "GET" "/query" {:qs {:q q}}))

;; -----------------------------------------------------------------------------
;; Contact

(defn more-details
  "Given a contact id, gives everything we got."
  [x]
  (req "GET" (str "/sobjects/Contact/" x)))

;; -----------------------------------------------------------------------------
;; Opportunities

(defn account->opportunity [x]
  (go (let [o (-> (query (str "
                           SELECT Opportunity.Id, Opportunity.Name
                           FROM Opportunity
                           WHERE Opportunity.AccountId = '" x "'"))
                  <!
                  :records
                  first)]
        (select-keys o [:Name :Id]))))

(defn contact->opportunity [x]
  (go (let [o (-> (query (str "
                           SELECT Opportunity.Id, Opportunity.Name
                           FROM Opportunity
                           WHERE Opportunity.AccountId IN (
                             SELECT AccountId
                             FROM Contact
                             WHERE Contact.Id = '" x "'
                           )
                           "))
                  <!
                  :records
                  first)]
        (select-keys o [:Name :Id]))))

(defn name->opportunity
  "given a name and a type of sobjects (Contact, Account, etc.)"
  [name type]
  (go (if-let [id (->> (search name)
                       <!
                       (filter (comp (partial = type) :type :attributes trace))
                       first
                       :Id)
               ]
        (<! (account->opportunity id))
        (println "account not found"))))

(defn get-latest-update
  "given a opp id, returns latest update"
  [op-id]
  (go (let [r (-> (req "GET" (str "/chatter/feeds/record/" op-id "/feed-elements"))
                  <!
                  :elements
                  first
                  :header
                  :text
                  )]
        r)))

(defn opportunity->last-event [x]
  (go (let [r (-> (query (str "SELECT Id, WhatId, WhoId FROM Event WHERE WhatId = '" x "'
                               ORDER BY LastModifiedDate DESC NULLS FIRST"))
               <!
               :records
               first)
         ]
        r)))

(defn opportunity->attendees [x]
  (go (let [r (<! (opportunity->last-event x))
            eid (:Id r)
            whoid (:WhoId r)
            who (-> (query (str "SELECT Id, Name, Title
                                 FROM Contact
                                 WHERE Contact.Id = '" whoid "'"))
                    <!
                    :records
                    first
                    trace
                    (select-keys [:Id :Name :Title]))
            attendees (-> (query (str "SELECT Id, Name, Title
                                       FROM Contact
                                       WHERE Contact.Id IN (
                                         SELECT RelationId
                                         FROM EventRelation
                                         WHERE EventId = '" eid "'
                                       )"))
                          <!
                          :records
                          trace
                          (->> (map #(select-keys % [:Id :Name :Title]))))
         ]
        (set (if who
               (conj attendees who)
               attendees)))))

(defn update-opportunity-size [opp-id to]
  (go (let [x (req "PATCH" (str "/sobjects/Opportunity/" opp-id)
                   {:body (js/JSON.stringify (clj->js {"Amount" to}))
                    :headers {:Content-Type "application/json"}})]
        (<! x))))

;; -----------------------------------------------------------------------------
;; Chatter

(defn chatter-send [name msg]
  (go (let [cid (->> (search name)
                     <!
                     (filter (comp (partial = "User") :type :attributes trace))
                     first
                     :Id)]
        (req "POST" "/chatter/users/me/messages" {:body (clj->js {:recipients [cid]
                                                                  :body msg})}))))

;; -----------------------------------------------------------------------------
;; Task

(defn set-task [uid d a]
  (go (let [r (-> (req "POST" "/sobjects/Task" {:body (clj->js {:OwnerId uid
                                                                :Subject a
                                                                :ActivityDate d})})
                  <!)]
        r)))

;; -----------------------------------------------------------------------------
;; Meeting

(defn create-meeting [d contact-name account-name]
  (go (let [acc-id (->> (search account-name)
                        <!
                        (filter (comp (partial = "Account") :type :attributes trace))
                        first
                        :Id)
            cid (->> (search contact-name)
                     <!
                     (filter (comp (partial = "Contact") :type :attributes trace))
                     first
                     :Id)
            op-id (-> (account->opportunity acc-id)
                      <!
                      :Id)
            r (-> (req "POST" "/sobjects/Event" {:body (clj->js {:Subject "Meeting"
                                                                 :WhatId op-id
                                                                 :ActivityDateTime d
                                                                 :WhoId cid
                                                                 :DurationInMinutes 60
                                                                 })}))]
        )))
