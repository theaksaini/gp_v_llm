import os
import json
from api_handler import AzureAPIHandler
from llmutils import sanitize_input, verify_response

def main():
    """Main function to handle question submission and training data."""

    # Ensure you set your API key
    api_key = os.environ.get('AZURE_OPENAI_API_KEY')
    api_handler = AzureAPIHandler()

    # Submit a question
    question = "Given a text string and a target string,return a vector of integers of the indices at which the target appears in the text. If the target string overlaps itself in the text, all indices(including those overlapping)should be returned."
    csv_path = "datasets/indices-of-substring/indices-of-substring_train.csv"
    sanitized_question = sanitize_input(question)
    response = api_handler.submit_question(question=sanitized_question, csv_path=csv_path)
    print("Question Response:")
    print(response)

    # Submit training data (assuming the CSV is placed in the data/ directory)
    csv_path = "datasets/indices-of-substring/indices-of-substring_train.csv"
    training_data_response = api_handler.submit_training_data(csv_path)
    print(training_data_response)

def load_and_execute_responses():
    """Load and execute function responses from responses.json."""

    with open("responses.json", "r") as file:
        for line in file:
            entry = json.loads(line)
            question = entry["question"]
            response = entry["response"]

            # Verify and extract the script
            script = verify_response(response)
            if not script:
                print(f"Invalid or no executable function in response for question: {question}")
                continue

            # Execute the function
            try:
                exec(script, globals())
                # Check and run the function "my_func"
                if 'my_func' in globals():
                    result = my_func()
                    print(f"Function executed successfully with result: {result}")
                else:
                    print("No 'my_func' defined in response script.")

                # Clean up the globals to avoid conflicts in subsequent loops
                globals().pop('my_func', None)
            except Exception as err:
                print(f"Error executing function for question '{question}': {err}")

if __name__ == "__main__":
    main()
