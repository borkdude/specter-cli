(ns specter-sci
  (:require [com.rpl.specter :as specter]
            [com.rpl.specter.impl :as i]
            [sci.core :as sci])
  (:gen-class))

(def sns (sci/create-ns 'com.rpl.specter nil))
(def ins (sci/create-ns 'com.rpl.specter.impl nil))

(def sci-ctx (volatile! nil))
(def tmp-closure (sci/new-dynamic-var '*tmp-closure* ins))

(defn closed-code
  "Patch for closed-code which uses clojure.core/eval which isn't possible in native-image."
  [closure body]
  (let [lv (mapcat #(vector % `(i/*tmp-closure* '~%))
                   (keys closure))]
    (sci/binding [tmp-closure closure]
      (sci/eval-form @sci-ctx `(let [~@lv] ~body)))))

;; Note that when using clojure.compiler.direct-linking=true you might have to
;; patch more functions, since they aren't going through the patched var.
(alter-var-root #'com.rpl.specter.impl/closed-code (constantly closed-code))

;; Private vars, used from path macro
(def ic-prepare-path #'specter/ic-prepare-path)
(def ic-possible-params #'specter/ic-possible-params)

(defmacro path
  "Patch for path macro which uses clojure.core/intern as a side effect,
  which is replaced by sci.core/intern here."
  [& path]
  (let [local-syms (-> &env keys set)
        used-locals (i/used-locals local-syms path)

        ;; note: very important to use riddley's macroexpand-all here, so that
        ;; &env is preserved in any potential nested calls to select (like via
        ;; a view function)
        expanded (i/clj-macroexpand-all (vec path))
        prepared-path (ic-prepare-path local-syms expanded)
        possible-params (vec (ic-possible-params expanded))

        cache-sym (vary-meta
                   (gensym "pathcache")
                   merge {:cljs.analyzer/no-resolve true :no-doc true :private true})

        info-sym (gensym "info")

        get-cache-code `(try (i/get-cell ~cache-sym)
                             (catch ClassCastException e#
                               ;; With AOT compilation it's possible for:
                               ;; Thread 1: unbound, so throw exception
                               ;; Thread 2: unbound, so throw exception
                               ;; Thread 1: do alter-var-root
                               ;; Thread 2: it's bound, so retrieve the current value
                               (if (bound? (var ~cache-sym))
                                 (i/get-cell ~cache-sym)
                                 (do
                                   (alter-var-root
                                    (var ~cache-sym)
                                    (fn [_#] (i/mutable-cell)))
                                   nil))))
        add-cache-code `(i/set-cell! ~cache-sym ~info-sym)
        precompiled-sym (gensym "precompiled")
        handle-params-code `(~precompiled-sym ~@used-locals)]
    ;; this is the actual patch
    (sci/intern @sci-ctx @sci/ns cache-sym (i/mutable-cell))
    ;; end patch
    `(let [info# ~get-cache-code

           info#
           (if (nil? info#)
             (let [~info-sym (i/magic-precompilation
                              ~prepared-path
                              ~(str *ns*)
                              (quote ~used-locals)
                              (quote ~possible-params))]
               ~add-cache-code
               ~info-sym)
             info#)

           ~precompiled-sym (i/cached-path-info-precompiled info#)
           dynamic?# (i/cached-path-info-dynamic? info#)]
       (if dynamic?#
         ~handle-params-code
         ~precompiled-sym))))


(defn make-ns
  "Copies public Clojure vars from namespace to a sci namespaces,
  transforming clojure Vars into sci vars. Returns map which can be
  used in :namespaces configuration."
  [ns sci-ns]
  (reduce (fn [ns-map [var-name var]]
            (let [m (meta var)
                  no-doc (:no-doc m)
                  doc (:doc m)
                  arglists (:arglists m)]
              (if no-doc ns-map
                  (assoc ns-map var-name
                         (sci/new-var (symbol var-name) @var
                                      (cond-> {:ns sci-ns
                                               :name (:name m)}
                                        (:macro m) (assoc :macro true)
                                        doc (assoc :doc doc)
                                        arglists (assoc :arglists arglists)))))))
          {}
          (ns-publics ns)))

(def ctx (sci/init {:namespaces
                    {'com.rpl.specter.impl
                     (assoc (make-ns 'com.rpl.specter.impl ins)
                            '*tmp-closure* tmp-closure)
                     'com.rpl.specter
                     (assoc (make-ns 'com.rpl.specter sns)
                            ;; the patched path macro
                            'path (sci/copy-var path sns))}
                    :classes {'java.lang.ClassCastException ClassCastException
                              'clojure.lang.Util clojure.lang.Util}}))

(vreset! sci-ctx ctx)

;; enable println, prn etc.
(sci/alter-var-root sci/out (constantly *out*))

(defn -main [& _]
  (sci/eval-string*
   ctx
   (pr-str
    '(do (use 'com.rpl.specter)

         (prn
          (transform [MAP-VALS MAP-VALS]
                     inc
                     {:a {:aa 1} :b {:ba -1 :bb 2}}))
         (prn
          (select [ALL ALL #(= 0 (mod % 3))]
                  [[1 2 3 4] [] [5 3 2 18] [2 4 6] [12]]))

         (prn
          (transform [(filterer odd?) LAST]
                     inc
                     [2 1 3 6 9 4 8]))

         (prn
          (setval [:a ALL nil?] NONE {:a [1 2 nil 3 nil]}))

         (prn
          (setval [:a :b :c] NONE {:a {:b {:c 1}}}))

         (prn
          (setval [:a (compact :b :c)] NONE {:a {:b {:c 1}}}))

         (prn
          (transform [(srange 1 4) ALL odd?] inc [0 1 2 3 4 5 6 7]))

         (prn
          (setval (srange 2 4) [:a :b :c :d :e] [0 1 2 3 4 5 6 7 8 9]))

         (prn
          (setval [ALL END] [:a :b] [[1] '(1 2) [:c]]))

         (prn
          (select (walker number?)
                  {2 [1 2 [6 7]] :a 4 :c {:a 1 :d [2 nil]}}))

         (prn
          (select ["a" "b"]
                  {"a" {"b" 10}}))

         (prn
          (transform [(srange 4 11) (filterer even?)]
                     reverse
                     [0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15]))

         (prn
          (setval [ALL
                   (selected? (filterer even?) (view count) (pred>= 2))
                   END]
                  [:c :d]
                  [[1 2 3 4 5 6] [7 0 -1] [8 8] []]))

         (prn
          (transform [ALL (collect-one :b) :a even?]
                     +
                     [{:a 1 :b 3} {:a 2 :b -10} {:a 4 :b 10} {:a 3}]))

         (prn
          (transform [:a (putval 10)]
                     +
                     {:a 1 :b 3}))

         (prn
          (transform [ALL (if-path [:a even?] [:c ALL] :d)]
                     inc
                     [{:a 2 :c [1 2] :d 4} {:a 4 :c [0 10 -1]} {:a -1 :c [1 1 1] :d 1}]))))))
