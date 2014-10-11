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
                               (try (let [x  (js->clj (js/JSON.parse msg) :keywordize-keys true)
                                          res (state/handle x)]
                                      (->> res
                                           clj->js
                                           js/JSON.stringify
                                           (.send ws)))
                                    (catch :default e
                                      (log "could not parse" msg)))))))
    (.on "error" (fn [err]
                   (log "error" err)))))

(defn ^:export main [& args]
  (let [port (js/parseInt (or js/process.env.PORT "1337"))]
    (connect! port)))

;; {:context ... :tts ...}

(set! *main-cli-fn* main)
