(ns voiceforce.state
  (:require [voiceforce.util :refer [trace log debug]]
            [cljs.nodejs :as nodejs]
            [cljs.core.async :as async :refer [<! >! take! put! chan timeout close!]]
            [cljs.core.match])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]
                   [cljs.core.match.macros :refer [match]]))

(defn drive-to
  [entities state]
  (let [op {}
        op-id (:id op)
        update (or (:update op) " Last price is $10M, updated last week by John")]
    (debug "drive-to " entities state)
    {:state {:op op-id}
     :text (str "Good luck! Here is the latest update about this opportunity: " update)}))

(defn who-attend
  [entities state]
  (let [op {}
        op-id (:id op)
        attendees (or (:attendees op) "Roberto Foo, CTO and Jessica Bar, CMO")]
    (debug "drive-to " entities state)
    {:state {:op op-id}
     :text attendees}))

(defn tell-more
  [entities state]
  (let [name (-> entities :name first :value)
        user {:id "123" :name "Roberto Foo"}
        lkdn-info {:bio "Roberto has been for 2y in SFDC..."}]
    {:state (merge state {:user (:id user)})
     :text (:bio lkdn-info)}))

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
                              (drive-to entities state)

                              ["who_attend"]
                              (who-attend entities state)

                              ["tell_more"]
                              (tell-more entities state)

                              :else
                              {:state state
                               :text "Sorry, I didn't get that"})]
    {:state (-> state clj->js js/JSON.stringify)
    :text text}))