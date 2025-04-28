;; solve_boolean.clj
;; Peter Kelly, pxkelly@hamilton.edu
;;
;; Problem inspired by: https://www.codewars.com/kata/59eb1e4a0863c7ff7e000008

(ns clojush.problems.psb2size200.solve-boolean
  (:use clojush.pushgp.pushgp
        [clojush pushstate interpreter random util globals]
        clojush.instructions.tag
        [clojure.math numeric-tower])
  (:require [psb2.core :as psb2]))

; Atom generators
(def atom-generators
  (make-proportional-atom-generators
   (concat
    (registered-for-stacks [:integer :boolean :exec :string :char]) ; stacks
    (list (tag-instruction-erc [:integer :boolean :exec :string :char] 1000) ; tags
          (tagged-instruction-erc 1000)))
   (list 'in1) ; inputs
   (list true
         false
         \t
         \f
         \&
         \|) ; constants
   {:proportion-inputs 0.15
    :proportion-constants 0.05}))

(defn solve-boolean-input
  "Creates an input for Solve Boolean, which is is a string of length terms, where each
   character in the string is either t, f, &, or |"
  [terms]
  (loop [in "" terms-left terms]
    (cond
      (= terms-left 0) (apply str in)
      (= terms-left 1) (recur (concat in (rand-nth '("t" "f"))) (dec terms-left))
      :else (recur (concat in (concat (rand-nth '("t" "f")) (rand-nth '("&" "|")))) (dec terms-left)))))

; A list of data domains for the problem. Each domain is a vector containing
; a "set" of inputs and two integers representing how many cases from the set
; should be used as training and testing cases respectively. Each "set" of
; inputs is either a list or a function that, when called, will create a
; random element of the set.
(def data-domains
  [[(list "t"
          "f"
          "f&f"
          "f&t"
          "t&f"
          "t&t"
          "f|f"
          "f|t"
          "t|f"
          "t|t") 10 0] ; "Special" inputs covering some base cases
   [(fn [] (solve-boolean-input (+ 2 (lrand-int 19)))) 190 2000]])

(defn bool-solve
  "Helper function to solve test cases"
  [bool]
  (cond
    (= bool "t|t") "t"
    (= bool "t|f") "t"
    (= bool "f|t") "t"
    (= bool "t&t") "t"
    :else "f"))

; Helper function for error function
(defn create-test-cases
  "Takes a sequence of inputs and gives IO test cases of the form
   [input output]."
  [inputs]
  (map (fn [in]
         (vector in
                 (loop [terms in]
                   (if (= (count terms) 1) (if (= terms "t") true false)
                       (recur (apply str (concat (bool-solve (subs terms 0 3)) (drop 3 terms))))))))
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
                   (for [[input1 correct-output] (case data-cases
                                                   :train train-cases
                                                   :test test-cases
                                                   data-cases)]
                     (let [final-state (run-push (:program individual)
                                                 (->> (make-push-state)
                                                      (push-item input1 :input)))
                           result (top-item :boolean final-state)]
                       (when print-outputs
                         (println (format "Correct output: %s | Program output: %s" correct-output (str result))))
                           ; Record the behavior
                       (swap! behavior conj result)
                           ; Error is right or wrong
                       (if (= correct-output result)
                         0
                         1))))]
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
  "For each entry in vec-of-maps, collects the vals with keys starting with 'input1' and 'output1' and makes a list of these values."
  [vec-of-maps]
  (mapv (fn [m]
          (vector (:input1 m) (:output1 m)))
        vec-of-maps))

(def train-and-test-data "Data taken from https://zenodo.org/record/5084812" (psb2/fetch-examples "data" "solve-boolean" 200 2000))

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
    (printf ";; -*- Solve Boolean problem report - generation %s\n" generation) (flush)
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
   :max-error 1})