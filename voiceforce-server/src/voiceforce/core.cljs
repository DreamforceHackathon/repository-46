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
                               (try (->> (process-msg msg)

                                         clj->js
                                         js/JSON.stringify
                                         (.send ws))
                                    (catch :default e
                                      (log "could not parse" msg e)))))))
    (.on "error" (fn [err]
                   (log "error" err))))

  (defn do! [req]
    (let [dt req]
      (go (-> (process-msg dt)
              <!
              println))))

  ;; (do! "{\"wit\":{\"stats\": {\"total_time\":13},\"msg_id\": \"fcca6b96-5bcc-4f9b-aecd-1001c10cb787\",\"_text\": \"driving to Uber\",\"outcomes\": [{\"_text\": \"driving to Uber\",\"intent\": \"drive_to\",\"entities\": {\"account\": [{\"value\": \"Uber\"}]},\"confidence\":0.505}]}}")

  ;; (do! "{\"wit\":{\"stats\": {\"total_time\":13},\"msg_id\": \"fcca6b96-5bcc-4f9b-aecd-1001c10cb787\",\"_text\": \"driving to Uber\",\"outcomes\": [{\"_text\": \"driving to Uber\",\"intent\": \"who_attend\",\"entities\": {\"account\": [{\"value\": \"Uber\"}]},\"confidence\":0.505}]}, \"state\":{\"opp\":\"006o0000004ny83\"}}")

(do! "{\"wit\":{\"stats\": {\"total_time\":13},\"msg_id\": \"fcca6b96-5bcc-4f9b-aecd-1001c10cb787\",\"_text\": \"driving to Uber\",\"outcomes\": [{\"_text\": \"driving to Uber\",\"intent\": \"tell_more\",\"entities\": {\"name\": [{\"value\": \"Edna\"}]},\"confidence\":0.505}]}, \"state\":{\"attendees\":[{\"Name\":\"Edna Frank\",\"Id\":\"003o000000BTNrm\"}]}}")

  )

(defn ^:export main [& args]
  (let [port (js/parseInt (or js/process.env.PORT "1337"))]
    (connect! port)))

;; {:context ... :tts ...}

(set! *main-cli-fn* main)
