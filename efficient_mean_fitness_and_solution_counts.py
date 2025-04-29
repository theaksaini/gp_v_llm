#!/usr/bin/python

## Can take 0, 1, or 2 command line arguments.
## If 0 arguments, uses variable set to outputDirectory as the location of the output files.
## First argument is the location of the output files, and overrides a variable defined below.
## Second argument is the prefix of the output files
## Third argument can be "brief" in order to not output individual run success/fail, and only output aggregate statistics
## Third argument can alternatively be "csv" in order to print one line per problem, ready to paste into a spreadsheet

import os, sys
from sys import maxsize
import datautils


# Some functions
def median(lst):
    if len(lst) <= 0:
        return False
    sorts = sorted(lst)
    length = len(lst)
    if not length % 2:
        return (sorts[length / 2] + sorts[length / 2 - 1]) / 2.0
    return sorts[int(length/2)]

def mean(nums):
    if len(nums) <= 0:
        return False
    return sum(nums) / float(len(nums))


def reverse_readline(filename, buf_size=8192):
    """A generator that returns the lines of a file in reverse order"""
    with open(filename, "rb") as fh:
        segment = None
        offset = 0
        fh.seek(0, os.SEEK_END)
        total_size = remaining_size = fh.tell()
        while remaining_size > 0:
            offset = min(total_size, offset + buf_size)
            fh.seek(-offset, os.SEEK_END)
            buffer = fh.read(min(remaining_size, buf_size))
            remaining_size -= buf_size
            lines = buffer.split(b'\n')
            if segment is not None:
                if not buffer.endswith(b'\n'):
                    lines[-1] += segment
                else:
                    yield segment.decode("utf-8")
            segment = lines[0]
            for index in range(len(lines) - 1, 0, -1):
                if lines[index]:
                    yield lines[index].decode("utf-8")
        if segment:
            yield segment.decode("utf-8")

def print_stats(outputDirectory, verbose=True):
    # Main area
    i = 0

    if outputDirectory[-1] != '/':
        outputDirectory += '/'
    dirList = os.listdir(outputDirectory)

    if not csv:
        print()
        print("           Directory of results:")
        print(outputDirectory)

    bestFitnessesOfRuns = []
    testFitnessOfBest = []
    testFitnessOfSimplifiedBest = []
    errorThreshold = maxsize
    errorThresholdPerCase = maxsize
    numCases = maxsize

    fileName0 = f"{outputFilePrefix}{i}{outputFileSuffix}/output.txt"
    f0 = open(outputDirectory + fileName0)

    for line in f0:
        if line.startswith("error-threshold"):
            try:
                errorThreshold = int(line.split()[-1])
            except ValueError:
                errorThreshold = float(line.split()[-1])
            if errorThreshold == 0:
                errorThresholdPerCase = 0

        if errorThresholdPerCase == maxsize and line.startswith("Errors:"):
            numCases = len(line.split()) - 1
            errorThresholdPerCase = float(errorThreshold) / numCases

        if errorThresholdPerCase != maxsize and numCases != maxsize:
            break

    while (outputFilePrefix + str(i) + outputFileSuffix) in dirList:
        if not csv:
            sys.stdout.write("%4i" % i)
            sys.stdout.flush()
            if i % 25 == 24:
                print

        runs = i + 1 # After this loop ends, runs should be correct
        #fileName = (outputFilePrefix + str(i) + outputFileSuffix)
        fileName = f"{outputFilePrefix}{i}{outputFileSuffix}/output.txt"
    #    f = open(outputDirectory + fileName)

        #final = False
        gen = 0
        best_mean_error = maxsize
        done = False

        bestTest = maxsize
        simpBestTest = maxsize

        if os.path.getsize(outputDirectory + fileName) == 0:
            bestFitnessesOfRuns.append((gen, best_mean_error, done))
            testFitnessOfBest.append(bestTest)
            testFitnessOfSimplifiedBest.append(simpBestTest)
            i += 1
            continue

        
        for line in reverse_readline(outputDirectory + fileName):

            if line.startswith(";; -*- Report") and gen == 0:
                gen = int(line.split()[-1])

            if gen != 0 and best_mean_error < maxsize:
                break

            if line.startswith("SUCCESS"):
                done = "SUCCESS"

            if line.startswith("FAILURE"):
                done = "FAILURE"

            if line.startswith("Mean:"):
                gen_best_error = -1
                if errorType == "float":
                    gen_best_error = float(line.split()[-1])
                elif errorType == "int" or errorType == "integer":
                    gen_best_error = int(line.split()[-1])
                else:
                    raise Exception("errorType of %s is not recognized" % errorType)

                if gen_best_error < best_mean_error:
                    best_mean_error = gen_best_error

            if line.startswith("Test total error for best:") and done:
                try:
                    bestTest = int(line.split()[-1].strip("Nn"))
                except ValueError:
                    bestTest = float(line.split()[-1].strip("Nn"))

            if line.startswith("Test total error for best:") and not done:
                try:
                    simpBestTest = int(line.split()[-1].strip("Nn"))
                except ValueError:
                    simpBestTest = float(line.split()[-1].strip("Nn"))

        bestFitnessesOfRuns.append((gen, best_mean_error, done))
        testFitnessOfBest.append(bestTest)
        testFitnessOfSimplifiedBest.append(simpBestTest)
                
        i += 1

    if not csv:
        print

    not_done = []
    not_done_gens = []
    if verbose:
        print("Error threshold per case:", errorThresholdPerCase)
        print("-------------------------------------------------")

        for i, (gen, fitness, done) in enumerate(bestFitnessesOfRuns):
            doneSym = ""
            if fitness <= errorThresholdPerCase:
                doneSym = " <- suc"
                if not done:
                    doneSym += "$$$$$$ ERROR $$$$$$" #Should never get here
                if len(testFitnessOfBest) > i and testFitnessOfBest[i] < maxsize:
                    if isinstance(testFitnessOfBest[i], int):
                        doneSym += " | test = %i" % testFitnessOfBest[i]
                    else:
                        doneSym += " | test = %.3f" % testFitnessOfBest[i]
                if len(testFitnessOfSimplifiedBest) > i and testFitnessOfSimplifiedBest[i] < maxsize:
                    if isinstance(testFitnessOfSimplifiedBest[i], int):
                        doneSym += " | test on simplified = %i" % testFitnessOfSimplifiedBest[i]
                    else:
                        doneSym += " | test on simplified = %.4f" % testFitnessOfSimplifiedBest[i] #TMH this line changed
            elif not done:
                doneSym = " -- not done"
                not_done.append(i)
                not_done_gens.append(gen)
            if fitness >= 0.001 or fitness == 0.0:
                print("Run: %3i  | Gen: %5i  | Best Fitness (mean) = %8.4f%s" % (i+1, gen, fitness, doneSym))
            else:
                print("Run: %3i  | Gen: %5i  | Best Fitness (mean) = %.4e%s" % (i+1, gen, fitness, doneSym))

    totalFitness = 0
    inds = 0
    perfectSolutions = 0
    perfectOnTestSet = 0
    simpPerfectOnTestSet = 0
    trainSolutionGens = []
    testSolutionGens = []

    for i, (gen, fitness, done) in enumerate(bestFitnessesOfRuns):
        if done:
            totalFitness += fitness
            inds += 1
            if fitness <= errorThresholdPerCase:
                perfectSolutions += 1
                trainSolutionGens.append(gen)
                if len(testFitnessOfBest) > i:
                    if testFitnessOfBest[i] <= errorThresholdPerCase:
                        perfectOnTestSet += 1
                        testSolutionGens.append(gen)
                if len(testFitnessOfSimplifiedBest) > i:
                    if testFitnessOfSimplifiedBest[i] <= errorThresholdPerCase:
                        simpPerfectOnTestSet += 1

    if verbose and len(trainSolutionGens) > 0:
        print("------------------------------------------------------------")
        print("Training Solution Generations:")
        print("Mean:      ", mean(trainSolutionGens))
        print("Minimum:   ", min(trainSolutionGens))
        print("Median:    ", median(trainSolutionGens))
        print("Maximum:   ", max(trainSolutionGens))

        if len(testSolutionGens) > 0:
            print("--------------------------")
            print("Test Solution Generations:")
            print("Mean:      ", mean(testSolutionGens))
            print("Minimum:   ", min(testSolutionGens))
            print("Median:    ", median(testSolutionGens))
            print("Maximum:   ", max(testSolutionGens))

            # print testSolutionGens

    if csv:
        print("%s,%i,%i,%i,%i" % (outputDirectory, inds, perfectSolutions, perfectOnTestSet, simpPerfectOnTestSet))

    else:
        print()
        print("------------------------------------------------------------")
        
        print("Number of finished runs:            %4i" % inds)
        print("Solutions found:                    %4i" % (perfectSolutions))
        print("Zero error on test set:             %4i" % perfectOnTestSet)
        print("Simplified zero error on test set:  %4i" % simpPerfectOnTestSet)
    
        print("------------------------------------------------------------")

        if verbose:
            print("Not done yet: ",)
            for run_i in not_done:
                sys.stdout.write("%i," % run_i)
            print()
            print("Not done generations: ",)
            for run_i in not_done_gens:
                sys.stdout.write("%i," % run_i)
            print()
            if not_done_gens:
                print("Max and Min generations: ", max([int(x) for x in not_done_gens]), min([int(x) for x in not_done_gens]))

            print("------------------------------------------------------------")
            if inds > 0:
                print("MBF: %.5f" % (totalFitness / float(inds)))


if __name__ == "__main__":

    #if (len(sys.argv) >= 2 and sys.argv[1] == "brief") or \
    #        (len(sys.argv) >= 3 and sys.argv[2] == "brief"):
    if (len(sys.argv) >= 4 and sys.argv[3] == "brief"):
        verbose = False

    csv = False

    # This allows this script to take a command line argument for outputDirectory
    if len(sys.argv) > 1 and sys.argv[1] != "brief" and sys.argv[1] != "csv":
        outputDirectory =sys.argv[1] 

    #outputFilePrefix = sys.argv[2]
    outputFilePrefix = "replicate_"
    outputFileSuffix = ""


    errorType = "float"

    if sys.argv[2]=="all":
        for task in datautils.PSB2_DATASETS:
            if os.path.exists(f"{outputDirectory}/{task}"):
                print("------------------------------------------------------------")
                print("Processing Task: ", task)
                print_stats(f"{outputDirectory}/{task}/", False)
            else:
                print("------------------------------------------------------------")
                print("Hasn't started on task: ", task)

    else:
        print_stats(f"{outputDirectory}/{sys.argv[2]}/output.txt", True)



