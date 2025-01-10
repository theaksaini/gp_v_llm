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

PSB2_DATASETS = ['basement', 'bouncing-balls', 'bowling', 'camel-case',
                 'coin-sums', 'cut-vector', 'dice-game', 'find-pair', 
                 'fizz-buzz', 'fuel-cost', 'gcd',  'indices-of-substring',
                 'leaders', 'luhn', 'mastermind',  'middle-character',
                 'paired-digits', 'shopping-list', 'snow-day',  'solve-boolean',
                   'spin-words', 'square-digits','substitution-cipher',
                   'twitter', 'vector-distance']

EMPTY_QUERY = [['','a vector of integers of length [1, 20] with each integer in [−100, 100]','an integer', 'input1', 0],
            ['', 'a float in [1.0, 100.0], float in [1.0, 100.0], integer in [1, 20]', 'a float', 'input1:float, input2:float, input3:int', 0], 
            ['', 'a string in form of completed bowling card, with one character per roll','an integer', 'input1:str', 0], 
            ['', 'a string of length [1, 20]', 'a string', 'input1:str', 0], 
            ['', 'an integer in [1, 10000]','4 integers', 'input1:int', 0], 
            ['', 'a vector of integers of length [1, 20] with each integer in [1, 10000]', '2 vectors of integers', 'input1', 0],
            ['','2 integers in [1, 1000]','a float', 'input1:int, input2:int', 0],
            ['','a vector of integers of length [2, 20] with each integer in [-10000, 10000], integer in [-20000, 20000]','2 integers', 'input1, input2:int', 0],
            ['','an integer in [1, 1000000]','a string', 'input1:int', 0],
            ['','a vector of integers of length [1, 20] with each integer in [6, 100000]','an integer', 'input1', 0],
            ['','2 integers in [1, 1000000]','an integer', 'input1:int, input2:int', 0],
            ['','2 strings of length [1, 20]','a vector of integers', 'input1:str, input2:str', 0],
            ['','a vector of integers of length [0, 20] with each integer in [0, 1000]','a vector of integers', 'input1', 0],
            ['','a vector of integers of length 16 with each integer in [1, 9]','an integer', 'input1', 0],
            ['','2 strings of length 4 made of B, R, W, Y, O, G','2 integers', 'input1:str, input2:str', 0], 
            ['','a string of length [1, 100]','a string', 'input1:str', 0],
            ['','a string of digits of length [2, 20]','an integer', 'input1:str', 0], 
            ['','a vector of floats of length [1, 20] with each float in [0.0, 50.0], a vector of floats of length [1, 20] with each float in [0.0, 100.0] where both vectors must be the same length',' a float', 'input1, input2', 0],
            ['','an integer in [0, 20],a float in [0.0, 20.0],a float in [0.0, 10.0],a float in [0.0, 1.0]','a float', 'input1:int, input2:float, input3:float, input4:float', 0],
            ['','a string of length [1, 20] made of characters from {t, f, |, &}','a Boolean', 'input1:str', 0],
            ['','a string of length [0, 20]','a string', 'input1:str', 0],
            ['','an integer in [0, 1000000]','a string', 'input1:int', 0],
            ['','3 strings of length [0, 26]','a string', 'input1:str, input2:str, input3:str',0 ],
            ['','string of length [0, 200]','a string', 'input1:str', 0],
            ['','2 vectors of floats of length [1, 20] with each float in [-100.0, 100.0]','a float', 'input1, input2', 0]]

FULL_QUERY = [['Given a vector of integers, return the first index such that the sum of all integers from the start of the vector to that index (inclusive) is negative.','a vector of integers of length [1, 20] with each integer in [−100, 100]','an integer', 'input1', 0],
            ['Given a starting height and a height after the first bounce of a dropped ball, calculate the bounciness index (height of first bounce / starting height). Then, given a number of bounces, use the bounciness index to calculate the total distance that the ball travels across those bounces.', 'a float in [1.0, 100.0], float in [1.0, 100.0], integer in [1, 20]', 'a float', 'input1:float, input2:float, input3:int', 0], 
            ['Given a string representing the individual bowls in a 10-frame round of 10 pin bowling, return the score of that round.', 'a string in form of completed bowling card, with one character per roll','an integer', 'input1:str', 0], 
            ['Take a string in kebab-case and convert all of the words to camelCase. Each group of words to convert is delimited by "-", and each grouping is separated by a space. For example: "camel-case example-test-string"->"camelCase exampleTestString".', 'a string of length [1, 20]', 'a string', 'input1:str', 0], 
            ['Given a number of cents, find the fewest number of US coins (pennies, nickles, dimes, quarters) needed to make that amount, and return the number of each type of coin as a separate output.', 'an integer in [1, 10000]','4 integers', 'input1:int', 0], 
            ['Given a vector of positive integers, find the spot where, if you cut the vector, the numbers on both sides are either equal, or the difference is as small as possible. Return the two resulting subvectors as two outputs.', 'a vector of integers of length [1, 20] with each integer in [1, 10000]', '2 vectors of integers', 'input1', 0],
            ['Peter has an n sided die and Colin has an m sided die. If they both roll their dice at the same time, return the probability that Peter rolls strictly higher than Colin.','2 integers in [1, 1000]','a float', 'input1:int, input2:int', 0],
            ['Given a vector of integers, return the two elements that sum to a target integer.','a vector of integers of length [2, 20] with each integer in [-10000, 10000], integer in [-20000, 20000]','2 integers', 'input1, input2:int', 0],
            ['Given an integer x, return "Fizz" if x is divisible by 3, "Buzz" if x is divisible by 5, "FizzBuzz" if x is divisible by 3 and 5, and a string version of x if none of the above hold.','an integer in [1, 1000000]','a string', 'input1:int', 0],
            ['Given a vector of positive integers, divide each by 3, round the result down to the nearest integer, and subtract 2. Return the sum of all of the new integers in the vector.','a vector of integers of length [1, 20] with each integer in [6, 100000]','an integer', 'input1', 0],
            ['Given two integers, return the largest integer that divides each of the integers evenly.','2 integers in [1, 1000000]','an integer', 'input1:int, input2:int', 0],
            ['Given a text string and a target string, return a vector of integers of the indices at which the target appears in the text. If the target string overlaps itself in the text, all indices (including those overlapping) should be returned.','2 strings of length [1, 20]','a vector of integers', 'input1:str, input2:str', 0],
            ['Given a vector of positive integers, return a vector of the leaders in that vector. A leader is defined as a number that is greater than or equal to all the numbers to the right of it. The rightmost element is always a leader.','a vector of integers of length [0, 20] with each integer in [0, 1000]','a vector of integers', 'input1', 0],
            ['Given a vector of 16 digits, implement Luhn\'s algorithm to verify a credit card number, such that it follows the following rules: double every other digit starting with the second digit. If any of the results are over 9, subtract 9 from them. Return the sum of all of the new digits.','a vector of integers of length 16 with each integer in [1, 9]','an integer', 'input1', 0],
            ['Based on the board game Mastermind. Given a Mastermind code and a guess, each of which are 4-character strings consisting of 6 possible characters, return the number of white pegs (correct color, wrong place) and black pegs (correct color, correct place) the codemaster should give as a clue.','2 strings of length 4 made of B, R, W, Y, O, G','2 integers', 'input1:str, input2:str', 0], 
            ['Given a string, return the middle character as a string if it is odd length; return the two middle characters as a string if it is even length.','a string of length [1, 100]','a string', 'input1:str', 0],
            ['Given a string of digits, return the sum of the digits whose following digit is the same.','a string of digits of length [2, 20]','an integer', 'input1:str', 0], 
            ['Given a vector of floats representing the prices of various shopping goods and another vector of floats representing the percent discount of each of those goods, return the total price of the shopping trip after applying the discount to each item.','a vector of floats of length [1, 20] with each float in [0.0, 50.0], a vector of floats of length [1, 20] with each float in [0.0, 100.0] where both vectors must be the same length',' a float', 'input1, input2', 0],
            ['Given an integer representing a number of hours and 3 floats representing how much snow is on the ground, the rate of snow fall, and the proportion of snow melting per hour, return the amount of snow on the ground after the amount of hours given. Each hour is considered a discrete event of adding snow and then melting, not a continuous process.','an integer in [0, 20],a float in [0.0, 20.0],a float in [0.0, 10.0],a float in [0.0, 1.0]','a float', 'input1:int, input2:float, input3:float, input4:float', 0],
            ['Given a string representing a Boolean expression consisting of T, F, |, and &, evaluate it and return the resulting Boolean.','a string of length [1, 20] made of characters from {t, f, |, &}','a Boolean', 'input1:str', 0],
            ['Given a string of one or more words (separated by spaces), reverse all of the words that are five or more letters long and return the resulting string.','a string of length [0, 20]','a string', 'input1:str', 0],
            ['Given a positive integer, square each digit and concatenate the squares into a returned string.','an integer in [0, 1000000]','a string', 'input1:int', 0],
            ['This problem gives 3 strings. The first two represent a cipher, mapping each character in one string to the one at the same index in the other string. The program must apply this cipher to the third string and return the deciphered message.','3 strings of length [0, 26]','a string', 'input1:str, input2:str, input3:str',0 ],
            ['Given a string representing a tweet, validate whether the tweet meets Twitter\'s original character requirements. If the tweet has more than 140 characters, return the string "Too many characters". If the tweet is empty, return the string "You didn\'t type anything". Otherwise, return "Your tweet has X characters", where the X is the number of characters in the tweet.','string of length [0, 200]','a string', 'input1:str', 0],
            ['Given two n-dimensional vectors of floats, return the Euclidean distance between the two vectors in n-dimensional space.','2 vectors of floats of length [1, 20] with each float in [-100.0, 100.0]','a float', 'input1, input2', 0]]

def main():
    """Main function to handle question submission and training data."""
    indexes = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24]
    test_names = [PSB2_DATASETS[x] for x in indexes]
    empty_query = [EMPTY_QUERY[x] for x in indexes]
    full_query = [FULL_QUERY[x] for x in indexes]
    #test_names = PSB2_DATASETS
    #empty_query = EMPTY_QUERY

    print(test_names)

    loop_through_datasets(dataset_names=test_names, query=full_query, portion='200', iterations=100)

    print('Run Finished')

def handle_subprocess_execution(function_response, inputdf, inputs, result, outputs, original_results,timeout=60):
    """Handle function execution in a subprocess with forced timeout."""
    queue = Queue()
    process = Process(target=execute_my_func, args=(queue, function_response, inputdf, inputs, result, outputs, original_results))
    
    process.start()
    process.join(timeout)

    if process.is_alive():
        print("Process taking too long to execute. Terminating forcefully.")
        process.terminate()
        process.join()
        return 0.0

    results = queue.get() if not queue.empty() else 0.0
    return results
    

def execute_my_func(queue, function_response, inputdf, inputs, result, outputs, original_results):
    """Executes my_func with a timeout."""
    namespace = {}
    code = """import math;"""
    code += """import numpy as np;"""
    codes = compile(code, '<string>', 'exec')
    exec(codes, namespace)
    exec(function_response, namespace)
    for key in namespace.keys():
        globals().pop(key, None)
        globals()[key] = namespace.get(key)
    my_func = namespace.get('my_func')
    if not my_func:
        queue.put(0.0)
        return
    try:
        # Using apply in a lambda that applies my_func to each row
        result[outputs] = inputdf.apply(
            lambda row: pd.Series(my_func(*[row[i] for i in inputs]), index=outputs),
            axis=1,
            result_type='expand'
        ).astype('string')
        # Compare results and return based on equality
        print(original_results.compare(result))
        percentages = original_results.eq(result).mean(axis=0).min()
        #print(percentages)
        if not np.isnan(percentages):
            queue.put(percentages)
            for key in namespace.keys():
                globals().pop(key, None)
            return 
        else:
            queue.put(0.0)
            for key in namespace.keys():
                globals().pop(key, None)
            return 
    except Exception as e:
        print(f"Error while executing 'my_func': {e}")
        queue.put(0.0)
        for key in namespace.keys():
                globals().pop(key, None)
        return
    
    

def load_and_execute_responses(function_response, csv_path):
    """Load and execute function responses from responses."""

    df = pd.read_csv(csv_path)
    colnames = list(df.columns.values)
    #print(colnames)
    inputs = [word for letter, words in groupby(sorted(colnames), key=itemgetter(0)) if letter == 'i' for word in words]
    outputs = [word for letter, words in groupby(sorted(colnames), key=itemgetter(0)) if letter != 'i' for word in words]

    #print(inputs)
    #print(outputs)
    inputdf = df[inputs]
    original_results = df[outputs].astype('string')
    result = original_results.copy()
    for col in result.columns:
        result[col].values[:] = '0'
    #print('Return False')
    #print(original_results.equals(result))

    return handle_subprocess_execution(function_response, inputdf, inputs, result, outputs, original_results)

def loop_through_datasets(dataset_names, query, iterations, portion, API=AzureAPIHandler):
    #print(dataset_names)
    #print(query)
    my_dict = dict(zip(dataset_names, query))
    #print(my_dict)
    total_vals = []
    
    for name in dataset_names:
        train_path = f"datasets/{name}/{name}_{portion}_train.csv"
        test_path = f"datasets/{name}/{name}_{portion}_test.csv"
        traincount = 0
        testcount = 0
        success_rates = []
        for i in range(iterations):
            api_key = os.environ.get('AZURE_OPENAI_API_KEY')
            api = API(dataset_name=name, api_key=api_key)
            function_response = api.submit_question(question=my_dict[name], csv_path=train_path, portion=portion, iteration=i+1)
            #print("Question Response:")
            #print(function_response)
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
        percent_dict = dict(zip([str(x) for x in range(iterations)], success_rates))
        with open(f'datasets/{name}/{name}_{portion}_questiononly_per_dict.csv', 'w') as csv_file:  
            writer = csv.writer(csv_file)
            for key, value in percent_dict.items():
                writer.writerow([key, value])
        result_vals = [traincount, testcount]
        print(result_vals)
        total_vals.append(result_vals)
        print(str(name)+' Complete')
    results_dict = dict(zip(dataset_names, total_vals))
    print(results_dict)
    with open('datasets/questiononly.csv', 'w') as csv_file:  
        writer = csv.writer(csv_file)
        for key, value in results_dict.items():
            writer.writerow([key, value])
    print(api.__class__.__name__)
    print('Complete')

if __name__ == "__main__":
    main()
