;; bouncing_balls.clj
;; Peter Kelly, pxkelly@hamilton.edu
;;
;; Problem inspired by: https://www.codewars.com/kata/5544c7a5cb454edb3c000047

(ns clojush.problems.psb2size200.bouncing-balls
  (:use clojush.pushgp.pushgp
        [clojush pushstate interpreter random util globals]
        clojush.instructions.tag
        clojure.math.numeric-tower)
  (:require [psb2.core :as psb2]))

; Atom generators
(def atom-generators
  (make-proportional-atom-generators
   (concat
    (registered-for-stacks [:integer :boolean :exec :float]) ;stacks
    (list (tag-instruction-erc [:exec :integer :boolean :float] 1000) ; tags
          (tagged-instruction-erc 1000)))
   (list 'in1 'in2 'in3) ; inputs
   (list 0.0 
         1.0 
         2.0) ; constants
   {:proportion-inputs 0.15
    :proportion-constants 0.05}))

; Define test cases
(defn bouncing-balls-input
  "Returns a vector of 3 inputs for Bouncing Balls. The first is the starting
   height of the ball, the second if the bounce height after the first bounce,
   and the last is the number of bounces"
  []
  (let [start-height (inc (rand 99))
        bounce-height (inc (* (rand) (dec start-height)))]
    (vector start-height bounce-height (inc (rand-int 20)))))

; A list of data domains for the Bouncing Balls problem. Each domain is a vector containing
; a "set" of inputs and two integers representing how many cases from the set
; should be used as training and testing cases respectively. Each "set" of
; inputs is either a list or a function that, when called, will create a
; random element of the set.
(def data-domains
  [[(list [1.001 1.0 1] ; Smallest input
          [100.0 99.999 20] ; Largest input
          [100.0 1.0 20] ; Low bounce index
          [15.319 5.635 1]
          [2.176 1.787 1]
          [17.165 5.627 1]
          [60.567 37.053 1]
          [62.145 62.058 1]
          [36.311 33.399 1]
          [46.821 8.151 1] ; One bounce inputs
          )10 0]
   [(fn [] (bouncing-balls-input)) 190 2000]])

; Helper function for error function
(defn create-test-cases
  "Takes a sequence of inputs and gives IO test cases of the form
   [[input1 input2 input3] output]."
  [inputs]
  (map (fn [[in1 in2 in3]]
         (vector [in1 in2 in3]
                 (let [bounce-index (float (/ in2 in1))]
                   (loop [start-height in1 bounce-height in2 bounces-left in3 distance 0]
                     (if (= bounces-left 0) distance
                         (recur bounce-height (* bounce-height bounce-index) (dec bounces-left) (+ distance start-height bounce-height)))))))
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
                   (for [[[input1 input2 input3] correct-output] (case data-cases
                                                                   :train train-cases
                                                                   :test test-cases
                                                                   data-cases)]
                     (let [final-state (run-push (:program individual)
                                                 (->> (make-push-state)
                                                      (push-item input3 :input)
                                                      (push-item input2 :input)
                                                      (push-item input1 :input)))
                           result (top-item :float final-state)]
                       (when print-outputs
                         (let [res-str (if (float? result)
                                         (format "%.3f" result)
                                         (str result))]
                           (println (format "Correct output: %.3f | Program output: %s" (float correct-output) res-str))))
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

(println "Working directory:" (System/getProperty "user.dir"))
;(println (read-csv-as-maps (str "../datasets/bouncing-balls/bouncing-balls_0_train.csv")))

;Define train and test cases
(def train-and-test-cases1
  (get-train-and-test data-domains))
(println train-and-test-cases1)


; Need to conver the maops to lists
; for examplel if train-and-test-data is the following;
; (def train-and-test-data {:train '({:input1 23, :input2 34, :output1 56} {:input1 2, :input2 3, :output1 5}), 
;                          :test '({:input1 39, :input2 349, :output1 569} {:input1 29, :input2 39, :output1 59}) })
; train-and-test-cases should be:
; [([[23 34] 56] [[2 3] 5]) ([[39 349] 569] [[29 39] 59])]

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

(def train-and-test-data "Data taken from https://zenodo.org/record/5084812" (psb2/fetch-examples "data" "bouncing-balls" 200 2000))

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
    (printf ";; -*- Bouncing Balls problem report - generation %s\n" generation) (flush)
    (println "Test total error for best:" best-total-test-error)
    (println (format "Test mean error for best: %.5f" (double (/ best-total-test-error (count best-test-errors)))))
    (when (zero? (:total-error best))
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