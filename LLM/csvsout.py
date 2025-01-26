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
import ast

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
    promptlist=['QuestionOnly']#['Austin2021', 'ChenEqual', 'ChenReturn', 'Sharlin2024', 'Wen2024']
    prompt_bothcount=['QuestionOnly_both']#['Austin2021_both', 'ChenEqual_both', 'ChenReturn_both', 'Sharlin2024_both', 'Wen2024_both']
    colnames = ['Task', 'Result']
    total_vals = []
    total_frame = pd.DataFrame(columns=promptlist)
    for i in test_names:
        newcolnames = [f'{i}_iteration', 'Result']
        localrow = []
        data = pd.read_csv(f'datasets/{i}/{i}_200_questiononly_per_dict.csv', names=newcolnames, header=None, converters={"Result": convert_to_list})
        df = data.drop('Result', axis=1, inplace=False)
        for promptcounter, j in enumerate(['_questiononly']):#['_austin2021', '_chenequal', '_chenreturn', '', '_wen2024']):
            data = pd.read_csv(f'datasets/{i}/{i}_200{j}_per_dict.csv', names=newcolnames, header=None, converters={"Result": convert_to_list})
            split = pd.DataFrame(data['Result'].to_list(), columns=[f'{promptlist[promptcounter]}_train', f'{promptlist[promptcounter]}_test'])
            print(split)
            split[f'{promptlist[promptcounter]}_both'] = np.where((split[f'{promptlist[promptcounter]}_train'] == 1.0) & (split[f'{promptlist[promptcounter]}_test'] == 1.0), 1.0, 0.0)
            count = ((split[f'{promptlist[promptcounter]}_train'] == 1.0) & (split[f'{promptlist[promptcounter]}_test'] == 1.0)).sum()
            localrow.append(count)
            df = pd.concat([df, split], axis=1)
        otherframe = {'QuestionOnly_both': localrow}
        #{'Austin2021_both': [localrow[0]],
        #              'ChenEqual_both': [localrow[1]], 
        #              'ChenReturn_both': [localrow[2]], 
        #              'Sharlin2024_both': [localrow[3]], 
        #              'Wen2024_both': [localrow[4]]}
        print(otherframe)
        outerframe = pd.DataFrame(otherframe)
        total_frame = pd.concat([total_frame, outerframe], ignore_index=True)
        df.to_csv(f'datasets/{i}/percent_questiononly_results.csv', index=False)
        print(df)

    df = pd.read_csv(f'datasets/questiononly.csv', names=colnames, header=None)
    df.drop('Result', axis=1, inplace=True)
    #print(df)
    for i in promptlist:
        data = pd.read_csv(f'datasets/{i}.csv', names=colnames, header=None, converters={"Result": convert_to_list})
        #print(data)
        #print(data['Result'].dtype)
        split = pd.DataFrame(data['Result'].to_list(), columns=[f'{i}_train', f'{i}_test'])
        #print(split)
        df = pd.concat([df, split], axis=1)
        df = pd.concat([df, total_frame[f'{i}_both']], axis=1)

    df.to_csv('datasets/questiononly_results.csv')
    print('Run Finished')

def convert_to_list(x):
    try: 
        return ast.literal_eval(x)
    except:
        try:
            newx = x.strip('[np.float64()]')
            #print(newx)
            newx = newx.replace('), np.float64(', ',')
            #print(newx)
            return [float(c) for c in newx.split(',')]
        except:
            try:
                newx = x.strip('[)]')
                #print(newx)
                newx = newx.replace(', np.float64(', ',')
                #print(newx)
                return [float(c) for c in newx.split(',')]
            except:
                newx = x.strip('[np.float64()]')
                #print(newx)
                newx = newx.replace('), ', ',')
                #print(newx)
                return [float(c) for c in newx.split(',')]


if __name__ == "__main__":
    main()