import numpy as np
import string


def integer_ERC():
    """Return a random integer."""
    return np.random.randint(-100, 100)

def float_ERC():
    """Return a random float."""
    return np.random.uniform(0, 100)

def char_digit_ERC():
    """Return a random digit from 0-9."""   
    return chr(np.random.randint(48, 58))

def visible_character_ERC():
    """Return a random visible character."""
    return chr(np.random.randint(97, 122))

def string_ERC():
    """Return a random string made up of a-z, A-B, and three other letters: _, -, ''."""
    length = np.random.randint(0, 21)
    characters = string.ascii_letters + "_- "
    return ''.join(np.random.choice(characters) for _ in range(length))

def vector_ERC():
    """Return a random vector of integers."""
    length = np.random.randint(0, 21)
    return [np.random.randint(0, 1000) for _ in range(length)]


