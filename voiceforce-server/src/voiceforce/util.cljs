(ns voiceforce.util
  (:require [cljs.nodejs :as nodejs]))

(def util (js/require "util"))

(def debug? (boolean (.-env.DEBUG js/process)))

(defn trace [x]
  (println x)
  x)

(defn debug [& args]
  (when debug? (apply println (cons "[voiceforce]" args))))

(defn log [& args]
  (apply println "[voiceforce]" args))
