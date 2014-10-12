(ns voiceforce.core
  (:require [cljs.nodejs :as nodejs]
            [cljs.core.async :as async :refer [<! >! take! put! chan timeout close!]]
            [goog.string :as gstring]
            [voiceforce.sfdc :as sf]
            [voiceforce.state :as state]
            [voiceforce.util :refer [trace log debug]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]))

(def request (js/require "request"))

(nodejs/enable-util-print!)

;; (defn )

(defn process-msg [msg]
  (-> msg
      js/JSON.parse
      (js->clj :keywordize-keys true)
      state/handle))

(def WebSocketServer (.-Server (js/require "ws")))

(defn connect!
  [voice-force-port]
  (log "starting ws server at" voice-force-port)
  (doto (WebSocketServer. #js {:port voice-force-port})
    (.on "connection" (fn [ws]
                        (.on ws "close"
                             (fn [ws]
                               (debug "closed ws")))
                        (.on ws "message"
                             (fn [msg]

                               (debug "Receiving: " msg)
                               (go
                                 (try (->> (process-msg msg)
                                           <!
                                           clj->js
                                           js/JSON.stringify
                                           (.send ws))
                                      (catch :default e
                                        (log "could not parse" msg e))))))))
    (.on "error" (fn [err]
                   (log "error" err))))

  (defn do! [req]
    (let [dt req]
      (go (-> (process-msg dt)
              <!
              println))))

;; (do! (-> {:intent "drive_to"
;;           :text "driving to Uber"
;;           :entities {:account [{:value "Facebook"}]}
;;           :state (js/JSON.stringify
;;                   (clj->js {:op "006o0000004ny83"
;;                             :account "Twitter"
;;                             :attendees [{:Name "Edna Frank", :Id "003o000000BTNrm"}]}))}
;;          clj->js
;;          js/JSON.stringify
;;          trace))

;; (do! (-> {:intent "who_attend"
;;           :text "driving to Uber"
;;           :entities {:amount_of_money [{:value 200000}]}
;;           :state (js/JSON.stringify
;;                   (clj->js {:op "006o0000004nzO9AAI"
;;                             :account "Twitter"
;;                             :attendees [{:Name "Edna Frank", :Id "003o000000BTNrm"}]}))}
;;          clj->js
;;          js/JSON.stringify
;;          trace))

(do! (-> {:intent "create_meeting"
          :text "driving to Uber"
          :entities {:datetime [{:value "2014-10-11T23:25:44.410Z"}]
                     :contact [{:value "Lauren"}]
                     :account [{:value "Facebook"}]}
          :state (js/JSON.stringify
                  (clj->js {:op "006o0000004nzO9AAI"
                            :account "Twitter"
                            :attendees [{:Name "Edna Frank", :Id "003o000000BTNrm"}]}))}
         clj->js
         js/JSON.stringify
         trace))


;; (do! (-> {:intent "tell_more"
;;           :text "driving to Uber"
;;           :entities {:name [{:value "John"}]}
;;           :state (js/JSON.stringify
;;                   (clj->js {:attendees [{:Name "Edna Frank", :Id "003o000000BTNrm"}]}))}
;;          clj->js
;;          js/JSON.stringify
;;          trace))

;; (do! (-> {:intent "inform"
;;           :text "driving to Uber"
;;           :entities {:name [{:value "Edna"}]}
;;           :state (js/JSON.stringify
;;                   (clj->js {:attendees [{:Name "Edna Frank", :Id "003o000000BTNrm"}]}))}
;;          clj->js
;;          js/JSON.stringify
;;          trace))

;; (do! (-> {:intent "task"
;;           :text "driving to Uber"
;;           :entities {:reminder [{:value "YAY 2MEETING"}] :datetime [{:value "2014-10-11T23:25:44.410Z"}]}
;;           :state (js/JSON.stringify
;;                   (clj->js {:attendees [{:Name "Edna Frank", :Id "003o000000BTNrm"}]}))}
;;          clj->js
;;          js/JSON.stringify
;;          trace))

  )

(defn ^:export main [& args]
  (let [port (js/parseInt (or js/process.env.PORT "1337"))]
    (connect! port)))

;; {:context ... :tts ...}

(set! *main-cli-fn* main)
