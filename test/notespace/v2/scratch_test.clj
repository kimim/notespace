(ns notespace.v2.scratch-test
  (:require [notespace.v2.api :as api :refer [check D]]
            [notespace.v2.note :as note
             :refer [note note-void
                     note-md note-hiccup
                     note-as-md note-as-hiccup]]
            [notespace.v2.live-reload]
            [notespace.v2.vega]
            [notespace.v2.init :as init]
            [notespace.v2.state :as state]
            [notespace.v2.basic-renderer :as basic-renderer]
            [clojure.core.async :as async]))

(comment
  (api/realize-this-notespace!)
  (state/kind->behaviour)
  (init/init!)
  @state/state)

["## A1 vector of strings is of kind `:md` by default."]

["
* a
* b
* c
"]

["## The old `note` notation works for all note kinds."]

(note
 (def x 1)
 (* x 2))

["## Otherwise, any expression is just a regular note."]

(* x 223)

["## Metadata can be used to override note kind."]

(note-as-hiccup
 (D
  (Thread/sleep 1000)
  [:h1 2]))


^:as-hiccup
[:div
 [:ul
  (for [i (range 3)]
    [:li i])]]

["## Delays are dereffed before rendering."]

(def y 111)

(delay
  (Thread/sleep 3000)
  (* y y))


["## `D` is an alias for `delay`."]

(D
  (Thread/sleep 1000)
  (* y 1))
