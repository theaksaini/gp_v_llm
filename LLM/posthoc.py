import os
import pandas as pd
import concurrent.futures
from multiprocessing import Process, Queue
import numpy as np
import csv
import json
from api_handler import AzureAPIHandler
from llmutils import sanitize_input, verify_response
from itertools import groupby
from operator import itemgetter
from main import load_and_execute_responses

PSB2_DATASETS = ['basement', 'bouncing-balls', 'bowling', 'camel-case',
                 'coin-sums', 'cut-vector', 'dice-game', 'find-pair', 
                 'fizz-buzz', 'fuel-cost', 'gcd',  'indices-of-substring',
                 'leaders', 'luhn', 'mastermind',  'middle-character',
                 'paired-digits', 'shopping-list', 'snow-day',  'solve-boolean',
                   'spin-words', 'square-digits','substitution-cipher',
                   'twitter', 'vector-distance']

def main():
    """Main function to handle question submission and training data."""

    test_names = PSB2_DATASETS
    #empty_query = [EMPTY_QUERY[0]]
    #full_query = FULL_QUERY

    print(test_names)

    posthoc_through_datasets(dataset_names=test_names, portion='200', iterations=100)

    print('Run Finished')

def posthoc_through_datasets(dataset_names, iterations, portion):
    #print(dataset_names)
    #print(query)
    #my_dict = dict(zip(dataset_names, query))
    #print(my_dict)
    total_vals = []
    
    for name in dataset_names:
        train_path = f"datasets/{name}/{name}_{portion}_train.csv"
        test_path = f"datasets/{name}/{name}_{portion}_test.csv"
        testcount = 0
        traincount = 0
        data=[]
        success_rates = []
        for i in range(iterations):
            try:
                with open(f'datasets/{name}/{portion}/responses/{name}_{str(i+1)}_test_responses.json', 'r') as f:
                    for line in f:
                        data.append(json.loads(line))
                function_response = data[-1]['response']
                print(function_response)
                train_success_rate = load_and_execute_responses(function_response, csv_path=train_path)
                test_success_rate = load_and_execute_responses(function_response, csv_path=test_path)
                if not (1.0-train_success_rate)>0:
                    traincount += 1
                if not (1.0-test_success_rate)>0:
                    testcount += 1
                print(train_success_rate)
                print(traincount)
                print(test_success_rate)
                print(testcount)
                percent_vals = [train_success_rate, test_success_rate]
                success_rates.append(percent_vals)
                print(str(i)+' Complete')
            except: 
                continue
        percent_dict = dict(zip([str(x) for x in range(iterations)], success_rates))
        with open(f'datasets/{name}/{name}_{portion}_test_per_dict.csv', 'w') as csv_file:  
            writer = csv.writer(csv_file)
            for key, value in percent_dict.items():
                writer.writerow([key, value])
        result_vals = [traincount, testcount]
        print(result_vals)
        total_vals.append(result_vals)
        print(str(name)+' Complete')
    results_dict = dict(zip(dataset_names, total_vals))
    print(results_dict)
    with open('datasets/Test.csv', 'w') as csv_file:  
        writer = csv.writer(csv_file)
        for key, value in results_dict.items():
            writer.writerow([key, value])
    print('Complete')

if __name__ == "__main__":
    main()