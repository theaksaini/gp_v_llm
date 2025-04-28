;; snow_day.clj
;; Peter Kelly, pxkelly@hamilton.edu
;;
;; Problem inspired by our introductory level programming course

(ns clojush.problems.psb2size200.snow-day
  (:use clojush.pushgp.pushgp
        [clojush pushstate interpreter random util globals]
        clojush.instructions.tag
        [clojure.math numeric-tower])
  (:require [psb2.core :as psb2]))

; Atom generators
(def atom-generators
  (make-proportional-atom-generators
   (concat
    (registered-for-stacks [:integer :float :boolean :exec])
    (list (tag-instruction-erc [:integer :float :boolean :exec] 1000) ; tags
          (tagged-instruction-erc 1000)))
   (list 'in1 'in2 'in3 'in4) ; inputs
   (list 0
         0.0
         1
         1.0
         -1
         -1.0) ; constants
   {:proportion-inputs 0.15
    :proportion-constants 0.05}))

; A list of data domains for the problem. Each domain is a vector containing
; a "set" of inputs and two integers representing how many cases from the set
; should be used as training and testing cases respectively. Each "set" of
; inputs is either a list or a function that, when called, will create a
; random element of the set.
(def data-domains
  [[(list [0 0.0 0.0 0.0] ; Min size
          [15 15.0 15.0 0.15] ; Same number
          [20 19.99 9.999 0.999] ; Max size
          [20 19.99 9.999 0.0] ; Max snow
          [10 0.0 1.0 0.0]
          [8 10.0 2.0 0.0]
          [13 0.0 0.0 0.0]
          [15 14.56 0.0 0.0]
          [16 18.19 0.0 0.05]
          [8 11.3 0.5 0.3]
          [5 1.3 1.5 0.05]
          [10 0.0 2.0 0.0]) 12 0]
   [(fn [] (vector (rand-int 21) (rand 20) (rand 10) (rand))) 188 2000] ; Random cases
   ])

; Helper function for error function
(defn create-test-cases
  "Takes a sequence of inputs and gives IO test cases of the form
   [[input1 input2 input3 input4] output]."
  [inputs]
  (map #(vector %
                (loop [time (first %)
                       total (second %)]
                  (if (= time 0)
                    total
                    (recur (dec time)
                           (+ (* total (- 1 (last %)))
                              (nth % 2))))))
       inputs))

(defn make-error-function-from-cases
  "Creates and returns the error function based on the train/test cases."
  [train-cases test-cases]
  (fn the-actual-error-function
    ([individual]
     (the-actual-error-function individual :train))
    ([individual data-cases] ; data-cases should be :train or :test
     (the-actual-error-function individual data-cases false))
    ([individual data-cases print-outputs]
     (let [behavior (atom '())
           errors (doall
                   (for [[[input1 input2 input3 input4] correct-output] (case data-cases
                                                                          :train train-cases
                                                                          :test test-cases
                                                                          data-cases)]
                     (let [final-state (run-push (:program individual)
                                                 (->> (make-push-state)
                                                      (push-item input4 :input)
                                                      (push-item input3 :input)
                                                      (push-item input2 :input)
                                                      (push-item input1 :input)))
                           result (top-item :float final-state)]
                       (when print-outputs
                         (let [res-str (if (float? result)
                                         (format "%.5f" result)
                                         (str result))]
                           (println (format "Correct output: %.5f | Program output: %s" (float correct-output) res-str))))
                           ; Record the behavior
                       (swap! behavior conj result)
                           ; Error is float error rounded to 3 decimal places
                       (round-to-n-decimal-places
                        (if (number? result)
                          (abs (- result correct-output)) ; distance from correct float
                          1000000.0) ; penalty for no return value
                        3))))]
       (if (= data-cases :test)
         (assoc individual :test-errors errors)
         (assoc individual
                :behaviors (reverse @behavior)
                :errors errors))))))

(defn get-train-and-test
  "Returns the train and test cases."
  [data-domains]
  (map create-test-cases
       (test-and-train-data-from-domains data-domains)))

; Define train and test cases
;(def train-and-test-cases
;  (get-train-and-test data-domains))

(defn problem-specific-maps-to-lists
  "For each entry in vec-of-maps, collects all vals with keys starting with 'input1', 'input2', etc. into a list.
   Then make a list of that list paired with the val of the key 'output1'."
  [vec-of-maps]
  (map (fn [m]
         (let [inputs (->> m
                           (filter (fn [[k _]] (re-matches #"input\d+" (name k))))
                           (sort-by (fn [[k _]] (Integer/parseInt (subs (name k) 5))))
                           (map second)
                           vec)
               output (:output1 m)]
           [inputs output]))
       vec-of-maps))

(def train-and-test-data "Data taken from https://zenodo.org/record/5084812" (psb2/fetch-examples "data" "snow-day" 200 2000))
;(println train-and-test-data)

(def train-and-test-cases
  (vector (problem-specific-maps-to-lists (:train train-and-test-data))
          (problem-specific-maps-to-lists (:test train-and-test-data))))

(defn initial-report
  [argmap]
  (println "Train and test cases:")
  (doseq [[i case] (map vector (range) (first train-and-test-cases))]
    (println (format "Train Case: %3d | Input/Output: %s" i (str case))))
  (doseq [[i case] (map vector (range) (second train-and-test-cases))]
    (println (format "Test Case: %3d | Input/Output: %s" i (str case))))
  (println ";;******************************"))

(defn custom-report
  "Custom generational report."
  [best population generation error-function report-simplifications]
  (let [best-test-errors (:test-errors (error-function best :test))
        best-total-test-error (apply +' best-test-errors)]
    (println ";;******************************")
    (printf ";; -*- Snow Day problem report - generation %s\n" generation) (flush)
    (println "Test total error for best:" best-total-test-error)
    (println (format "Test mean error for best: %.5f" (double (/ best-total-test-error (count best-test-errors)))))
    (when (<= (:total-error best) 0.001)
      (doseq [[i error] (map vector
                             (range)
                             best-test-errors)]
        (println (format "Test Case  %3d | Error: %s" i (str error)))))
    (println ";;------------------------------")
    (println "Outputs of best individual on training cases:")
    (error-function best :train true)
    (println ";;******************************")
    )) ; To do validation, could have this function return an altered best individual
       ; with total-error > 0 if it had error of zero on train but not on validation
       ; set. Would need a third category of data cases, or a defined split of training cases.


; Define the argmap
(def argmap
  {:error-function (make-error-function-from-cases (first train-and-test-cases)
                                                            (second train-and-test-cases))
   :training-cases (first train-and-test-cases)
   :atom-generators atom-generators
   :max-points 2000
   :max-genome-size-in-initial-program 250
   :evalpush-limit 2000
   :population-size 1000
   :max-generations 1200
   :parent-selection :downsampled-lexicase
   :downsample-factor 0.25
   :genetic-operator-probabilities {:uniform-addition-and-deletion 1.0}
   :uniform-addition-and-deletion-rate 0.09
   :problem-specific-report custom-report
   :problem-specific-initial-report initial-report
   :report-simplifications 0
   :final-report-simplifications 5000
   :max-error 1000000.0})