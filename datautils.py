import pandas as pd
import csv
import GP.gp_utils as gp_utils


PSB2_DATASETS = ['basement', 'bouncing-balls', 'bowling', 'camel-case', 
                 'coin-sums', 'cut-vector', 'dice-game', 'find-pair', 
                 'fizz-buzz', 'fuel-cost', 'gcd',  'indices-of-substring',  
                 'leaders', 'luhn', 'mastermind',  'middle-character',  
                 'paired-digits', 'shopping-list', 'snow-day',  'solve-boolean',
                   'spin-words', 'square-digits','substitution-cipher', 
                   'twitter', 'vector-distance']

FULL_QUERY = ['Given a vector of integers, return the first index such that \
            the sum of all integers from the start of the vector to that \
            index (inclusive) is negative.', 'Given a starting height and \
            a height after the first bounce of a dropped ball, calculate \
            the bounciness index (height of first bounce / starting height). \
            Then, given a number of bounces, use the bounciness index to \
            calculate the total distance that the ball travels across those \
            bounces.', 'Given a string representing the individual bowls in a \
            10-frame round of 10 pin bowling, return the score of that round.',
            'Take a string in kebab-case and convert all of the words to \
            camelCase. Each group of words to convert is delimited by "-", \
            and each grouping is separated by a space. For example: \
            "camel-case example-test-string"->"camelCase exampleTestString".', 
            'Given a number of cents, find the fewest number of US coins \
            (pennies, nickles, dimes, quarters) needed to make that amount, \
            and return the number of each type of coin as a separate output.',
            'Given a vector of positive integers, find the spot where, \
            if you cut the vector, the numbers on both sides are either equal, \
            or the difference is as small as possible. Return the two resulting\
             subvectors as two outputs.', 'Peter has an n sided die and Colin \
            has an m sided die. If they both roll their dice at the same time, \
            return the probability that Peter rolls strictly higher than Colin.',
            'Given a vector of integers, return the two elements that sum to a \
            target integer.', 'Given an integer x, return "Fizz" if x is \
            divisible by 3, "Buzz" if x is divisible by 5, "FizzBuzz" if x \
            is divisible by 3 and 5, and a string version of x if none of the \
            above hold.', 'Given a vector of positive integers, divide each by \
            3, round the result down to the nearest integer, and subtract 2. \
            Return the sum of all of the new integers in the vector.', 
            'Given two integers, return the largest integer that divides each \
            of the integers evenly.', 'Given a text string and a target string,\
             return a vector of integers of the indices at which the target \
            appears in the text. If the target string overlaps itself in the \
            text, all indices (including those overlapping) should be returned.',
            'Given a vector of positive integers, return a vector of the \
            leaders in that vector. A leader is defined as a number that is \
            greater than or equal to all the numbers to the right of it. The \
            rightmost element is always a leader.', 'Given a vector of 16 \
            digits, implement Luhn\'s algorithm to verify a credit card number,\
             such that it follows the following rules: double every other digit\
             starting with the second digit. If any of the results are over 9, \
            subtract 9 from them. Return the sum of all of the new digits.',
            'Based on the board game Mastermind. Given a Mastermind code and a \
            guess, each of which are 4-character strings consisting of 6 \
            possible characters, return the number of white pegs (correct \
            color, wrong place) and black pegs (correct color, correct place) \
            the codemaster should give as a clue.', 'Given a string, return \
            the middle character as a string if it is odd length; return the \
            two middle characters as a string if it is even length.',
            'Given a string of digits, return the sum of the digits whose \
            following digit is the same.', 'Given a vector of floats \
            representing the prices of various shopping goods and another \
            vector of floats representing the percent discount of each of \
            those goods, return the total price of the shopping trip after \
            applying the discount to each item.', 'Given an integer \
            representing a number of hours and 3 floats representing how much \
            snow is on the ground, the rate of snow fall, and the proportion \
            of snow melting per hour, return the amount of snow on the ground \
            after the amount of hours given. Each hour is considered a \
            discrete event of adding snow and then melting, not a continuous \
            process.', 'Given a string representing a Boolean expression \
            consisting of T, F, |, and &, evaluate it and return the resulting \
            Boolean.', 'Given a string of one or more words (separated by \
            spaces), reverse all of the words that are five or more letters \
            long and return the resulting string.', 'Given a positive integer, \
            square each digit and concatenate the squares into a returned \
            string.', 'This problem gives 3 strings. The first two represent a \
            cipher, mapping each character in one string to the one at the \
            same index in the other string. The program must apply this cipher \
            to the third string and return the deciphered message.',
            'Given a string representing a tweet, validate whether the tweet \
            meets Twitter\'s original character requirements. If the tweet has \
            more than 140 characters, return the string "Too many characters". \
            If the tweet is empty, return the string "You didn\'t type \
            anything". Otherwise, return "Your tweet has X characters", where \
            the - is the number of characters in the tweet.', 'Given two \
            n-dimensional vectors of floats, return the Euclidean distance \
            between the two vectors in n-dimensional space.']

EMPTY_QUERY = ['', '', '', '', '', '', '', '', '', '', '', '', '', '', '',  '',  
              '', '', '', '', '', '', '', '', '']



def generate_training_test_data(data_dir, dataset_name, rand_seed, portion):
    '''
    Read from two csv files corresponding to a dataset containg 'edge cases' and 'random cases'.
    Create dataframes X_train and Y_train of size 200 that includes all egde cases and, if neddeed, rest from random cases dataset. 
    X_val and Y_val contain 200 cases from random cases.
    X_test and Y_test contain 2000 cases from random cases.

    Parameters:
    data_dir: str: directory containing the dataset
    dataset_name: str: name of the dataset
    rand_seed: int: random seed for reproducibility

    Returns:
    X_train, y_train, X_val, y_val, X_test, y_test: DataFrames: training and testing data
    '''

    edge_case = pd.read_csv(f"{data_dir}/{dataset_name}/{dataset_name}-edge.csv")
    random_cases = pd.read_csv(f"{data_dir}/{dataset_name}/{dataset_name}-random.csv")

    assert len(edge_case) <= 200, "The code assumes that the edge cases file contains at most 200 cases."

    # Ensure we have 200 training cases
    train = pd.concat([edge_case, random_cases.sample(n=portion - len(edge_case), 
                                                      random_state=rand_seed)])
    train = train.sample(frac=1).reset_index(drop=True)
    input_cols = [col for col in train.columns if col.startswith("input")]
    train.to_csv(f"{data_dir}/{dataset_name}/{dataset_name}_{str(portion)}_train.csv", 
                 index=False)
    X_train = train[input_cols]
    y_train = train.drop(columns=input_cols)
    
    # Ensure we have 2000 test cases
    val_test = random_cases.sample(n=2200, random_state=rand_seed)
    val = val_test.iloc[:200]
    test = val_test.iloc[200:]
    input_cols = [col for col in val.columns if col.startswith("input")]
    val.to_csv(f"{data_dir}/{dataset_name}/{dataset_name}_{str(portion)}_val.csv",
                 index=False)
    X_val = val[input_cols]
    y_val = val.drop(columns=input_cols)

    test.to_csv(f"{data_dir}/{dataset_name}/{dataset_name}_{str(portion)}_test.csv",
                 index=False)
    X_test = test[input_cols]
    y_test = test.drop(columns=input_cols)

    return X_train, y_train, X_test, y_test

def get_problem_metadata(metadata_file, problem):
    """Extracts the relevant information from the datasets_info.csv file for a given problem, to be passed to the GeneSpawner constructor."""
    
    datasets_info = pd.read_csv(metadata_file)

    # Change the 'Problem' column to lowercase, and replace spaces with hyphens
    datasets_info["Problem"] = datasets_info["Problem"].str.lower().str.replace(" ", "-")

    # Filter the datasets_info DataFrame to only include the rows with the problem name
    datasets_info = datasets_info[datasets_info["Problem"] == problem]

    n_inputs = int(datasets_info["n_inputs"].values[0])
    
    instruction_types = ["exec", "integer",  "float", "Boolean", "char",  "string",  "vector of integers",  "vector of floats"]
    instructions_columns = datasets_info[instruction_types]
    instruction_set = set(instructions_columns.columns[instructions_columns.eq(1).any()])

    literals = datasets_info["Constants and ERCs"].values[0].split(", ")
    # Partition the 'literals' list into two lists: one for the string with end with 'ERC' and one for the rest
    erc_literals= [literal for literal in literals if literal.endswith("ERC")]
    erc_generators = [getattr(gp_utils, literal.replace(" ", "_")) for literal in erc_literals]

    # Assert that the erc_generators list contains functions
    assert all([callable(erc_generator) for erc_generator in erc_generators])

    non_erc_literals = [literal for literal in literals if not literal.endswith("ERC")]

    return {
        "n_inputs": n_inputs,
        #"instruction_set": InstructionSet().register_core_by_stack(instruction_set),
        "literals": non_erc_literals,
        "erc_generators": erc_generators,
    }

if __name__ == "__main__":
    for names in PSB2_DATASETS:
        for i in [200]:
            X_train, y_train, X_test, y_test = generate_training_test_data(data_dir=\
                'datasets', dataset_name=names, rand_seed=42, portion=i)
            print(X_train, y_train, X_test, y_test, i)
        print(names)
        print("DONE")
