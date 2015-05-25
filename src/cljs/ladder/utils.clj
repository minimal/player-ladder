(ns ladder.utils)

(defmacro logm
  "Log as a macro to give the caller's line number in web console"
  ([s]
   `(.log js/console (print-str ~s)))
  ([s & rest]
   `(.log js/console (print-str (list ~s ~@rest)))))

(defmacro make-table-cols
  "Build a table row with collumns "
  [el attrs cols]
  {:pre [(contains? #{'dom/td 'dom/th} el)]}
  `(dom/tr attrs
           ~@(for [col cols]
               `(~el nil ~col))))
(comment
  (.log js/console (str (cons ~s ~rest)))
  (fn [& args]
    (.apply js/console.log js/console (into-array args))))

                                        ; Case 1: Show the state of a bunch of variables.
                                        ;
                                        ;   > (inspect a b c)
                                        ;
                                        ;   a => 1
                                        ;   b => :foo-bar
                                        ;   c => ["hi" "world"]
                                        ;
                                        ; Case 2: Print an expression and its result.
                                        ;
                                        ;   > (inspect (+ 1 2 3))
                                        ;
                                        ;   (+ 1 2 3) => 6
                                        ;

(defn- inspect-1 [expr]
  `(let [result# ~expr]
     (js/console.info (str (pr-str '~expr) " => " (pr-str result#)))
     result#))

(defmacro inspect [& exprs]
  `(do ~@(map inspect-1 exprs)))

                                        ; -------------------------------------------------------------------
                                        ; BREAKPOINT macro
                                        ; (use to stop the program at a certain point,
                                        ; then resume with the browser's debugger controls)
                                        ; NOTE: only works when browser debugger tab is open

(defmacro breakpoint []
  '(do (js* "debugger;")
       nil)) ; (prevent "return debugger;" in compiled javascript)
