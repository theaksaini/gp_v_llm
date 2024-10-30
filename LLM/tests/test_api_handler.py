import unittest
from llmutils import sanitize_input, verify_response

class TestAPIHandler(unittest.TestCase):

    def test_sanitize_input(self):
        self.assertEqual(sanitize_input("Hello, World!"), "Hello World")
        self.assertEqual(sanitize_input("123!@#"), "123")
        self.assertEqual(sanitize_input(""), "")

    def test_verify_response(self):
        valid_response = {
            "choices": [{"text": "def my_func(): return 42"}]
        }
        invalid_response = {
            "choices": [{"text": "No function here"}]
        }
        self.assertIsNotNone(verify_response(valid_response))
        self.assertIsNone(verify_response(invalid_response))

if __name__ == '__main__':
    unittest.main()
