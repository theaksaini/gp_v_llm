;; shopping_list.clj
;; Peter Kelly, pxkelly@hamilton.edu
;;
;; Problem inspired by: https://www.codewars.com/kata/596266482f9add20f70001fc

(ns clojush.problems.psb2size50.shopping-list
  (:use clojush.pushgp.pushgp
        [clojush pushstate interpreter random util globals]
        clojush.instructions.tag
        [clojure.math numeric-tower])
  (:require [psb2.core :as psb2]))

; Atom generators
(def atom-generators
  (make-proportional-atom-generators
   (concat
    (registered-for-stacks [:integer :boolean :exec :float :vector_float]) ; stacks
    (list (tag-instruction-erc [:integer :boolean :exec :float :vector_float] 1000) ; tags
          (tagged-instruction-erc 1000)))
   (list 'in1 'in2) ; inputs
   (list 0.0
         100.0 ; constants
         (fn [] (* (lrand) 100))) ; float ERC [0, 100]
   {:proportion-inputs 0.15
    :proportion-constants 0.05}))

(defn shopping-list-input
  "Makes a Shopping List input, which is two vectors of length len, the first with
   floats in range [0, 50] rounded to 2 decimal places and the second with floats
   in range [0, 100] rounded to 2 decimal places."
  [len]
  (vector (vec (repeatedly len #(round-to-n-decimal-places (* (rand) 50) 2)))
          (vec (repeatedly len #(round-to-n-decimal-places (* (rand) 100) 2)))))

; A list of data domains for the problem. Each domain is a vector containing
; a "set" of inputs and two integers representing how many cases from the set
; should be used as training and testing cases respectively. Each "set" of
; inputs is either a list or a function that, when called, will create a
; random element of the set.
(def data-domains
  [[(list [[50.00] [100.00]]
          [[50.00] [10.00]]
          [[20.00 20.00] [100.00 50.00]]
          [[20.00 20.00] [20.00 0.00]]
          [[10.00 20.00 30.00] [5.00 10.00 95.00]]
          [[43.14 18.23 5.33 1.35 39.68] [100.00 100.00 100.00 100.00 100.00]]
          [[5.73 5.73 5.73 5.73 5.73 5.73 5.73 5.73 5.73 5.73 5.73 5.73 5.73 5.73 5.73 5.73 5.73 5.73 5.73 5.73]
           [59.19 91.24 25.93 16.18 24.65 61.96 67.91 43.87 36.23 34.30 96.27 69.25 73.78 0.52 8.91 39.18 79.67 64.22 14.15 52.44]]
          [[25.43 43.22 23.42 42.09 25.70] [0.00 0.00 0.00 0.00 0.00]]
          [[0.01 0.01 0.01 0.01 0.01 0.01 0.01] [85.77 43.99 22.78 34.14 34.12 8.54 11.03]]
          [[9.99 9.99 9.99 9.99 9.99 9.99 9.99 9.99 9.99 9.99] [33.65 33.65 33.65 33.65 33.65 33.65 33.65 33.65 33.65 33.65]])
    10 0] ; "Special" inputs covering some base cases
   [(fn [] (shopping-list-input (inc (lrand-int 20)))) 190 2000]])

; Helper function for error function
(defn create-test-cases
  "Takes a sequence of inputs and gives IO test cases of the form
   [[input1 input2] output]."
  [inputs]
  (map (fn [[in1 in2]]
         (vector [in1 in2]
                 (let [discount (map #(float (/ (- 100 %) 100)) in2)]
                   (round-to-n-decimal-places (reduce + (vec (map #(* % %2) in1 discount))) 2))))
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
                   (for [[[input1 input2] correct-output] (case data-cases
                                                            :train train-cases
                                                            :test test-cases
                                                            data-cases)]
                     (let [final-state (run-push (:program individual)
                                                 (->> (make-push-state)
                                                      (push-item input2 :input)
                                                      (push-item input1 :input)))
                           result (top-item :float final-state)]
                       (when print-outputs
                         (let [res-str (if (float? result)
                                         (format "%.2f" result)
                                         (str result))]
                           (println (format "Correct output: %.2f | Program output: %s" (float correct-output) res-str))))
                         ; Record the behavior
                       (swap! behavior conj result)
                         ; Error is float error rounded to 2 decimal places
                       (round-to-n-decimal-places
                        (if (number? result)
                          (abs (- result correct-output)) ; distance from correct float
                          1000000.0) ; penalty for no return value
                        2))))]
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

(def train-and-test-data "Data taken from https://zenodo.org/record/5084812" (psb2/fetch-examples "data" "shopping-list" 50 2000))
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
    (printf ";; -*- Shopping List problem report - generation %s\n" generation) (flush)
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
   :final-report-simplifications 5000
   :max-error 1000000.0})