(ns voiceforce.sfdc
  (:require [cljs.core.async :as async :refer [<! >! take! put! chan timeout close!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]))

(def request (js/require "request"))
(def cons-key "3MVG9xOCXq4ID1uGUZEDkWbuTVfqOqP3IWRgPCy.sxHJ1edmupOjoYnEnuM1KrGWPF3QBa3yYEYHvz5lu038y")
(def cons-secret "8065890795745227552")
(def secret-token "rC7fjvarT8AOYemUI68abKEQ")
(def access-token "00Do0000000ciyx!ARQAQAr6iXYgniJIm6vBRaIfsPwGvf_3vqoENmkDgpvEs9XYfH6MGeChjU4NdkpclzZgydKPJvPZ6YneRqxvTcG.8PnMCCcr")

(defn sf-url [x]
  (str "https://na17.salesforce.com/services/data/v26.0" x))

(defn trace [x]
  (println "TRACE" x)
  x)

(defn req [meth url & [opts]]
  (let [out (chan)
        opts' (merge-with
               merge
               {:method meth
                :url  (sf-url url)
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
                   (put! out body))
                 :else
                 (js/console.log
                  (str "ERROR sending req to salesforce (http " (.-statusCode res) ")")
                  (js->clj res :keywordize-keys true)))))
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

(defn attendees [x]
  )

(defn more-details
  "Given a contact id, gives everything we got."
  [x]
  (let []
    (req "GET" (str "/sobjects/Contact/" x))))

;; -----------------------------------------------------------------------------
;; Opportunities

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

(defn name->opportunity [name]
  (go (if-let [id (->> (search name)
                       <!
                       (filter (comp (partial = "Contact") :type :attributes))
                       first
                       :Id)
               ]
        (<! (contact->opportunity id))
        (println "contact not found"))))


(defn upgrade-opportunity-size [opp-id to]
  (go (let [x (req "PATCH" (str "/sobjects/Opportunity/" opp-id)
                   {:body (js/JSON.stringify (clj->js {"Amount" to}))
                    :headers {:Content-Type "application/json"}})]
        (<! x))))
