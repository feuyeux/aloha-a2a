"""Tools available to the Dice Agent."""

import logging
import random
from typing import List

logger = logging.getLogger(__name__)


def roll_dice(sides: int) -> int:
    """
    Rolls an N-sided dice and returns the result.
    
    Args:
        sides: The number of sides on the dice (must be positive)
        
    Returns:
        The result of the dice roll (1 to N)
        
    Raises:
        ValueError: If sides is not positive
    """
    if sides <= 0:
        logger.error(f"Invalid dice sides: {sides}")
        raise ValueError("Dice must have at least 1 side")
    
    result = random.randint(1, sides)
    logger.info(f"Rolled {sides}-sided dice: {result}")
    return result


def check_prime(numbers: List[int]) -> str:
    """
    Checks which numbers in the list are prime.
    
    Args:
        numbers: List of integers to check
        
    Returns:
        A string describing which numbers are prime
    """
    if not numbers:
        return "No numbers provided to check."
    
    primes = [n for n in numbers if is_prime(n)]
    
    if not primes:
        logger.info(f"No prime numbers found in: {numbers}")
        return "None of the numbers are prime."
    
    result = ", ".join(str(p) for p in primes) + " are prime numbers."
    logger.info(f"Prime check for {numbers}: {result}")
    return result


def is_prime(n: int) -> bool:
    """
    Checks if a number is prime.
    
    Args:
        n: The number to check
        
    Returns:
        True if the number is prime, False otherwise
    """
    if n <= 1:
        return False
    if n == 2:
        return True
    if n % 2 == 0:
        return False
    
    sqrt_n = int(n ** 0.5)
    for i in range(3, sqrt_n + 1, 2):
        if n % i == 0:
            return False
    
    return True
