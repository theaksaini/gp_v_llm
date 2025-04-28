import pandas as pd
import GP.gp_utils as gp_utils

PSB2_DATASETS = ['basement', 'bouncing-balls', 'bowling', 'camel-case', 'coin-sums', 'cut-vector', 'dice-game', 'find-pair', 'fizz-buzz', 'fuel-cost', 'gcd',  'indices-of-substring',  'leaders', 'luhn', 'mastermind',  'middle-character',  'paired-digits', 'shopping-list', 'snow-day',  'solve-boolean', 'spin-words', 'square-digits','substitution-cipher', 'twitter', 'vector-distance']

def generate_training_test_data(data_dir, dataset_name, rand_seed):
    '''
    Read from two csv files corresponding to a dataset containg 'edge cases' and 'random cases'.
    Create dataframes X_train and Y_train of size 200 that includes all egde cases and, if neddeed, rest from random cases dataset. 
    X_test and Y_test contain 2000 cases from random cases.

    Parameters:
    data_dir: str: directory containing the dataset
    dataset_name: str: name of the dataset
    rand_seed: int: random seed for reproducibility

    Returns:
    X_train, y_train, X_test, y_test: DataFrames: training and testing data
    '''

    edge_case = pd.read_csv(f"{data_dir}/{dataset_name}/{dataset_name}-edge.csv")
    random_cases = pd.read_csv(f"{data_dir}/{dataset_name}/{dataset_name}-random.csv")

    # Ensure we have 200 training cases
    train = pd.concat([edge_case, random_cases.sample(n=200 - len(edge_case), random_state=rand_seed)])
    input_cols = [col for col in train.columns if col.startswith("input")]
    X_train = train[input_cols]
    y_train = train.drop(columns=input_cols)


    # Ensure we have 2000 test cases
    test = random_cases.sample(n=2000, random_state=rand_seed)
    input_cols = [col for col in test.columns if col.startswith("input")]
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
        "instruction_set": InstructionSet().register_core_by_stack(instruction_set),
        "literals": non_erc_literals,
        "erc_generators": erc_generators,
    }