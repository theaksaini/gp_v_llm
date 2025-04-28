;; middle_character.clj
;; Tom Helmuth, thelmuth@hamilton.edu
;;
;; Problem inspired by: https://www.codewars.com/kata/56747fd5cb988479af000028

(ns clojush.problems.psb2size200.middle-character
  (:use clojush.pushgp.pushgp
        [clojush pushstate interpreter random util globals]
        clojush.instructions.tag)
  (:require [clojure.math.numeric-tower :as nt]
            [psb2.core :as psb2]))

; Atom generators
(def atom-generators
  (make-proportional-atom-generators
   (concat
    (registered-for-stacks [:string :char :integer :boolean :exec]) ; stacks
    (list (tag-instruction-erc [:string :char :integer :boolean :exec] 1000) ; tags
          (tagged-instruction-erc 1000)))
   (list 'in1) ; inputs
   (list 0
         1
         2
         "" ; constants
         (fn [] (- (lrand-int 201) 100))) ;Integer ERC 
   {:proportion-inputs 0.15
    :proportion-constants 0.05}))

(def middle-character-chars (map char (range 32 127)))

(defn middle-character-input
  "Creates a string of length len only using the middle-character-chars."
  [len]
  (apply str
         (repeatedly len
                     #(lrand-nth middle-character-chars))))

; A list of data domains. Each domain is a vector containing
; a "set" of inputs and two integers representing how many cases from the set
; should be used as training and testing cases respectively. Each "set" of
; inputs is either a list or a function that, when called, will create a
; random element of the set.
(def data-domains
  [[(list "Q"
          " "
          "$"
          "E9"
          ")b"
          "DOG"
          "OGD"
          "test"
          "$3^:1"
          "middle"
          "testing"
          "      "
          "hi  ~1"
          "  hi~1"
          "hi~1  ") 15 0] ; Fixed strings
   [(fn [] (middle-character-input (inc (lrand-int 100)))) 185 2000] ; Random strings, length [1, 100]
   ])

(defn solve-middle-character
  "Solves the problem given the input."
  [input]
  (let [len (count input)]
    (if (zero? (mod len 2))
      (let [middle-left (dec (quot len 2))]
        (subs input middle-left (+ 2 middle-left)))
      (let [middle (quot len 2)]
        (str (nth input middle))))))

; Helper function for error function
(defn create-test-cases
  "Takes a sequence of inputs and gives IO test cases of the form
   [input output]."
  [inputs]
  (map (fn [in]
         (vector in
                 (solve-middle-character in)))
       (sort-by count inputs)))

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
                           result (top-item :string final-state)]
                       (when print-outputs
                         (println (format "Correct output: %s | Program output: %s" correct-output (str result))))
                         ; Record the behavior
                       (swap! behavior conj result)
                         ; Error is Levenshtein distance
                       (if (string? result)
                         (levenshtein-distance correct-output result)
                         1000) ; penalty for no return value
                       )))]
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

(def train-and-test-data "Data taken from https://zenodo.org/record/5084812" (psb2/fetch-examples "data" "middle-character" 200 2000))

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
    (printf ";; -*- Find Pair problem report - generation %s\n" generation) (flush)
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
   :max-error 1000})