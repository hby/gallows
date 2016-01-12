(ns gallows.state
  (:require [reagent.core :as reagent :refer [atom]]))


;;; Game state is held in Reagent atoms

; One off messages
(defonce message (atom ""))

; The player's state is a name
(defonce player (atom ""))

; The player's word
(defonce word (atom ""))

; All of the other player data
; [{ :name ""   ; player name
;    :id   ""   ; player id
;    :word "" } ; word to guess
;  ...]
(defonce players (atom []))

; If there is a game then this has game state,
; otherwise nil.
; {:player _   ; player data that has the word we're playing
;  :hangman _  ; word being played as a vector of single char strings
;  :letters _  ; letters to choose from, vector of single char strings
;  :guessed _  ; set of strings containing the guessed letters
;  :correct _} ; set of strings containing the correct guesses
(defonce game (atom nil))

;; Vector of strings that are the win/lose reports on this players words
(defonce reports (atom []))

