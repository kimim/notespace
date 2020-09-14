(ns notespace.note
  (:require [notespace.reader :as reader]
            [rewrite-clj.node]
            [notespace.source :as source]
            [notespace.view :as view]
            [notespace.state :as state]
            [notespace.util :as u]))


;; A note has a static part: a kind, possibly a label, a collection of forms, and the reader metadata,
;; and a dynamic part: a value, a realized-value and a status.
(defrecord Note [kind label forms metadata
                 value realized-value status])

;; TODO: Where is is used?
(defn note->index [namespace note]
  (->> note
       :metadata
       :line
       (state/sub-get-in :ns->line->index namespace)))

;; We can collect all toplevel forms in a namespace,
;; together with the reader metadata.
(defn ->ns-topforms-with-metadata [namespace]
  (->> namespace
       source/ns->source-filename
       reader/file->topforms-with-metadata
       (filter (comp :line meta))))

(defn ns-topform? [topform]
  (and (sequential? topform)
       (-> topform
           first
           (= 'ns))))

(defn strings-topform? [topform]
  (and (sequential? topform)
       (-> topform
           first
           string?)))

(defn vector-beginning-with-keyword-topform? [topform]
  (and (vector? topform)
       (-> topform
           first
           keyword?)))

(defn kinds-set []
  (-> :kind->behaviour
      state/sub-get-in
      keys
      set))

(defn metadata->kind [m]
  (some->> m
           :tag
           resolve
           deref
           ((kinds-set))))

(defn topform-with-metadata->kind [tfwm]
  (or
   (-> tfwm meta metadata->kind)
   (cond
     ;;
     (strings-topform? tfwm)
     :notespace.kinds/md
     ;;
     (vector-beginning-with-keyword-topform? tfwm)
     (->> tfwm first name (keyword "notespace.kinds"))
     ;;
     :else :notespace.kinds/naive)))

(defn topform-with-metadata->forms [tfwm]
  (if (-> tfwm meta :multi)
    tfwm
    [tfwm]))

;; Each toplevel form can be converted to a Note.
(defn topform-with-metadata->Note [tfwm]
  (let [m (meta tfwm)]
    (when-not (ns-topform? tfwm)
      (->Note (topform-with-metadata->kind tfwm)
              (:label m)
              (topform-with-metadata->forms tfwm)
              m
              :value/not-ready
              nil
              {:stage :initial}))))

;; Thus we can collect all notes in a namespace.
(defn ns-notes [namespace]
  (->> namespace
       ->ns-topforms-with-metadata
       (map topform-with-metadata->Note)
       (filter some?)))

(def ^:dynamic *notespace-idx* nil)

(defn note-evaluation [idx note]
  (binding [*notespace-idx* idx]
    (try
      (->> note
           :forms
           (cons 'do)
           eval)
      (catch Exception e
        (throw (ex-info "Note evaluation failed."
                        {:note      note
                         :exception e}))) )))

(defn evaluated-note [idx note]
  (assoc
   note
   :value (note-evaluation idx note)
   :status {:stage :evaluated}))

(defn realizing-note [note]
  (assoc
   note
   :status {:stage :realizing}))

(defn realized-note [note]
  (assoc
   note
   :status {:stage :realized}
   :realized-value (-> note :value u/realize)))

;; TODO: Rethink
(defn different-note? [old-note new-note]
  (or (->> [old-note new-note]
           (map (comp :source :metadata))
           (apply not=))
      (->> [old-note new-note]
           (map (juxt :kind :forms))
           (apply not=))))

(defn merge-note [old-note new-note]
  (if (different-note? old-note new-note)
    new-note
    (merge old-note
           (select-keys new-note [:metadata]))))

(defn merge-notes [old-notes
                   new-notes]
  (mapv merge-note
        (concat old-notes (repeat nil))
        new-notes))
