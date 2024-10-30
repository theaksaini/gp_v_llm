import os
import json
from api_handler import APIHandler
from utils import sanitize_input, verify_response

def main():
    """Main function to handle question submission and training data."""

    # Ensure you set your API key
    api_key = os.getenv("OPENAI_API_KEY")
    api_handler = APIHandler(api_key)

    # Submit a question
    question = "What is the capital of France?"
    sanitized_question = sanitize_input(question)
    response = api_handler.submit_question(sanitized_question)
    print("Question Response:")
    print(response)

    # Submit training data (assuming the CSV is placed in the data/ directory)
    csv_path = "data/training_data.csv"
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
                print(f"Invalid or no executable function in response for 
                      question: {question}")
                continue

            # Execute the function
            try:
                exec(script, globals())
                # Check and run the function "my_func"
                if 'my_func' in globals():
                    result = my_func()
                    print(f"Function executed successfully with result: 
                          {result}")
                else:
                    print("No 'my_func' defined in response script.")

                # Clean up the globals to avoid conflicts in subsequent loops
                globals().pop('my_func', None)
            except Exception as err:
                print(f"Error executing function for question '{question}': 
                      {err}")

if __name__ == "__main__":
    main()
