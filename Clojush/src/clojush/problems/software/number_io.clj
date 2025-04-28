;; number_io.clj
;; Tom Helmuth, thelmuth@cs.umass.edu
;;
;; Problem Source: iJava (http://ijava.cs.umass.edu/)
;;
;; This problem file defines the following problem:
;; There are two inputs, a float and an int. The program must read them in,
;; find their sum as a float, and print the result as a float.
;;
;; NOTE: input stack: in1 (float), in2 (int)
;;       output stack: printed output

(ns clojush.problems.software.number-io
  (:use clojush.pushgp.pushgp
        [clojush pushstate interpreter random util globals]
        clojush.instructions.tag
        clojure.math.numeric-tower
        ))

; Atom generators
(def num-io-atom-generators
  (concat (list
            (fn [] (- (lrand-int 201) 100))
            (fn [] (- (* (lrand) 200) 100.0))
            (tag-instruction-erc [:float :integer] 1000)
            (tagged-instruction-erc 1000)
            ;;; end ERCs
            'in1
            'in2
            ;;; end input instructions
            )
          (registered-for-stacks [:float :integer :print])))

;; A list of data domains for the number IO problem. Each domain is a vector containing
;; a "set" of inputs and two integers representing how many cases from the set
;; should be used as training and testing cases respectively. Each "set" of
;; inputs is either a list or a function that, when called, will create a
;; random element of the set.
(def num-io-data-domains
  [[(fn [] (vector (- (* (lrand) 200) 100.0) (- (lrand-int 201) 100))) 25 1000] ;; Each input is a float and an int, both from range [-100,100]
   ])

;;Can make number-IO test data like this:
;(test-and-train-data-from-domains num-io-data-domains)

; Helper function for error function
(defn num-io-test-cases
  "Takes a sequence of inputs and gives IO test cases of the form
   [[float-input int-input] output]."
  [inputs]
  (map #(vector %
                (apply + %))
       inputs))

(defn make-number-io-error-function-from-cases
  [train-cases test-cases]
  (fn the-actual-num-io-error-function
    ([individual]
      (the-actual-num-io-error-function individual :train))
    ([individual data-cases] ;; data-cases should be :train or :test
     (the-actual-num-io-error-function individual data-cases false))
    ([individual data-cases print-outputs]
      (let [behavior (atom '())
            errors (flatten
                     (doall
                       (for [[[in-float in-int] out-float] (case data-cases
                                                             :train train-cases
                                                             :test test-cases
                                                             data-cases)]
                         (let [final-state (run-push (:program individual)
                                                     (->> (make-push-state)
                                                       (push-item in-int :input)
                                                       (push-item in-float :input)
                                                       (push-item "" :output)))
                               printed-result (stack-ref :output 0 final-state)]
                           (when print-outputs
                             (println (format "Correct output: %-14s | Program output: %-14s" (pr-str (round-to-n-decimal-places out-float 10)) printed-result)))
                           ; Record the behavior
                           (swap! behavior conj printed-result)
                           ; Each test case results in two error values:
                           ;   1. Numeric difference between correct output and the printed
                           ;      output read into a float, rounded to 4 decimal places;
                           ;      if such a conversion fails, the error is a penalty of 1000.
                           ;   2. Levenstein distance between printed output and correct output as strings
                           (vector (round-to-n-decimal-places
                                     (try (min 1000.0 (abs (- out-float (Double/parseDouble printed-result))))
                                       (catch Exception e 1000.0))
                                     4)
                                   (levenshtein-distance printed-result (pr-str (round-to-n-decimal-places out-float 10))))))))]
        (if (= data-cases :test)
          (assoc individual :test-errors errors)
          (assoc individual :behaviors @behavior :errors errors))))))

(defn get-number-io-train-and-test
  "Returns the train and test cases."
  [data-domains]
  (map num-io-test-cases
       (test-and-train-data-from-domains data-domains)))

; Define train and test cases
(def number-io-train-and-test-cases
  (get-number-io-train-and-test num-io-data-domains))

(defn number-io-initial-report
  [argmap]
  (println "Train and test cases:")
  (doseq [[i case] (map vector (range) (first number-io-train-and-test-cases))]
    (println (format "Train Case: %3d | Input/Output: %s" i (str case))))
  (doseq [[i case] (map vector (range) (second number-io-train-and-test-cases))]
    (println (format "Test Case: %3d | Input/Output: %s" i (str case))))
  (println ";;******************************"))

(defn num-io-report
  "Custom generational report."
  [best population generation error-function report-simplifications]
  (let [best-test-errors (:test-errors (error-function best :test))
        best-total-test-error (apply +' best-test-errors)]
    (println ";;******************************")
    (printf ";; -*- Number IO problem report - generation %s\n" generation)(flush)
    (println "Test total error for best:" best-total-test-error)
    (println (format "Test mean error for best: %.5f" (double (/ best-total-test-error (count best-test-errors)))))
    (when (<= (:total-error best) 1.0E-4)
      (doseq [[i [num-error lev-dist]] (map vector
                                            (range)
                                            (partition 2 best-test-errors))]
        (println (format "Test Case  %3d | Numeric Error: %19.14f | Levenshtein Distance: %d" i (float num-error) lev-dist))))
    (println ";;------------------------------")
    (println "Outputs of best individual on training cases:")
    (error-function best :train true)
    (println ";;******************************")
    )) ;; To do validation, could have this function return an altered best individual
       ;; with total-error > 0 if it had error of zero on train but not on validation
       ;; set. Would need a third category of data cases, or a defined split of training cases.


; Define the argmap
(def argmap
  {:error-function (make-number-io-error-function-from-cases (first number-io-train-and-test-cases)
                                                             (second number-io-train-and-test-cases))
   :training-cases (first number-io-train-and-test-cases)
   :atom-generators num-io-atom-generators
   :max-points 800
   :max-genome-size-in-initial-program 100
   :evalpush-limit 200
   :population-size 1000
   :max-generations 200
   :parent-selection :lexicase
   :epigenetic-markers []
   :genetic-operator-probabilities {:alternation 0.3
                                    :uniform-mutation 0.2
                                    [:alternation :uniform-mutation] 0.5
                                    }
   :alternation-rate 0.01
   :alignment-deviation 5
   :uniform-mutation-rate 0.01
   :problem-specific-report num-io-report
   :problem-specific-initial-report number-io-initial-report
   :report-simplifications 0
   :final-report-simplifications 5000
   :max-error 5000
   :error-threshold 1.0E-4
   })
