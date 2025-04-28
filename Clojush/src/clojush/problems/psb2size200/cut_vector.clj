;; cut_vector.clj
;; Peter Kelly, pxkelly@hamilton.edu
;;
;; Problem inspired by: https://www.codewars.com/kata/5719b28964a584476500057d

(ns clojush.problems.psb2size200.cut-vector
  (:use clojush.pushgp.pushgp
        [clojush pushstate interpreter random util globals]
        clojush.instructions.tag
        [clojure.math numeric-tower combinatorics])
  (:require [psb2.core :as psb2]))

(define-registered
  output_vector_integer1
  ^{:stack-types [:vector_integer]}
  (fn [state]
    (if (empty? (:vector_integer state))
      state
      (let [top-int (top-item :vector_integer state)]
        (stack-assoc top-int :output 0 state)))))

(define-registered
  output_vector_integer2
  ^{:stack-types [:vector_integer]}
  (fn [state]
    (if (empty? (:vector_integer state))
      state
      (let [top-int (top-item :vector_integer state)]
        (stack-assoc top-int :output 1 state)))))

; Atom generators
(def atom-generators
  (make-proportional-atom-generators
   (concat
    (registered-for-stacks [:vector_integer :integer :boolean :exec]) ;stacks
    (list (tag-instruction-erc [:vector_integer :integer :boolean :exec] 1000) ; tags
          (tagged-instruction-erc 1000)))
   (list 'in1) ; inputs
   (list 0 
         []) ; constants
   {:proportion-inputs 0.15
    :proportion-constants 0.05}))

; Define test cases
(defn cut-vector-input
  "Makes a Cut Vector input vector of length len, which is just a vector
   of random numbers from 0-10,000."
  [len]
  (vec (repeatedly len #(rand-int 10001))))

; A list of data domains for the problem. Each domain is a vector containing
; a "set" of inputs and two integers representing how many cases from the set
; should be used as training and testing cases respectively. Each "set" of
; inputs is either a list or a function that, when called, will create a
; random element of the set.
(def data-domains
  [[(list [0] [10] [100] [1000] [10000]) 5 0] ; Length-1 vectors
   [(fn [] (cut-vector-input 1)) 20 250] ; Random length-1 vectors
   [(list [2 129]
          [0 40]
          [9999 74]
          [9879 9950]
          [9225 9994]) 5 0] ; Length-2 vectors
   [(fn [] (cut-vector-input 2)) 20 250] ; Random length-2 vectors
   [(fn [] (cut-vector-input (+ 3 (lrand-int 3)))) 50 500] ; Random length-3, -4, and -5 vectors
   [(fn [] (cut-vector-input 20)) 5 50] ; Random length-20 vectors
   [(fn [] (cut-vector-input (inc (lrand-int 20)))) 95 950] ; Random length, random ints
   ])

; Helper function for error function
(defn create-test-cases
  "Takes a sequence of inputs and gives IO test cases of the form
   [input [output1 output2]]."
  [inputs]
  (map (fn [in]
         (vector in
                 (loop [cut 1 minimum (apply max in) min-index 0]
                   (cond
                     (= (count in) 1) [in []]
                     (= (count in) cut) [(subvec in 0 min-index) (subvec in min-index)]
                     (= (reduce + (subvec in 0 cut)) (reduce + (subvec in cut))) [(subvec in 0 cut) (subvec in cut)]
                     (>= minimum (abs (- (reduce + (subvec in 0 cut)) (reduce + (subvec in cut))))) (recur (inc cut) (abs (- (reduce + (subvec in 0 cut)) (reduce + (subvec in cut)))) cut)
                     :else (recur (inc cut) minimum min-index)))))
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
           errors
           (flatten
            (doall
             (for [[input1 [correct-output1 correct-output2]] (case data-cases
                                                                :train train-cases
                                                                :test test-cases
                                                                data-cases)]
               (let [final-state (run-push (:program individual)
                                           (->> (make-push-state)
                                                (push-item :no-output :output)
                                                (push-item :no-output :output)
                                                (push-item input1 :input)))
                     result1 (stack-ref :output 0 final-state)
                     result2 (stack-ref :output 1 final-state)]
                 (when print-outputs
                   (println (format "Correct output: %s %s\n| Program output: %s %s\n" (str correct-output1) (str correct-output2) (str result1) (str result2))))
                       ; Record the behavior
                 (swap! behavior conj result1 result2)
                       ; Error is integer error at each position in the vectors, with additional penalties for incorrect size vector
                 (vector
                  (if (vector? result1)
                    (+' (apply +' (map (fn [cor res]
                                         (abs (- cor res)))
                                       correct-output1
                                       result1))
                        (*' 10000 (abs (- (count correct-output1) (count result1))))) ; penalty of 10000 times difference in sizes of vectors
                    1000000) ; penalty for no return value
                  (if (vector? result2)
                    (+' (apply +' (map (fn [cor res]
                                         (abs (- cor res)))
                                       correct-output2
                                       result2))
                        (*' 10000 (abs (- (count correct-output2) (count result2))))) ; penalty of 10000 times difference in sizes of vectors
                    1000000) ; penalty for no return value
                  )))))]
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
         (let [outputs (->> m
                            (filter (fn [[k _]] (re-matches #"output\d+" (name k))))
                            (sort-by (fn [[k _]] (Integer/parseInt (subs (name k) 6))))
                            (map second)
                            vec)
               inputs (:input1 m)]
           [inputs outputs]))
       vec-of-maps))

(def train-and-test-data "Data taken from https://zenodo.org/record/5084812" (psb2/fetch-examples "data" "cut-vector" 200 2000))

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
  (let [best-with-test (error-function best :test)
        best-test-errors (:test-errors best-with-test)
        best-total-test-error (apply +' best-test-errors)]
    (println ";;******************************")
    (printf ";; -*- Cut Vector problem report - generation %s\n" generation) (flush)
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
   :max-error 1000000})