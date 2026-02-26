/**
 * Tools available to the Dice Agent.
 */

/**
 * Rolls an N-sided dice and returns the result.
 * 
 * @param sides - The number of sides on the dice (must be positive)
 * @returns The result of the dice roll (1 to N)
 * @throws Error if sides is not positive
 */
export function rollDice(sides: number): number {
    if (sides <= 0) {
        throw new Error('Dice must have at least 1 side');
    }

    const result = Math.floor(Math.random() * sides) + 1;
    console.log(`Rolled ${sides}-sided dice: ${result}`);
    return result;
}

/**
 * Checks which numbers in the list are prime.
 * 
 * @param numbers - List of integers to check
 * @returns A string describing which numbers are prime
 */
export function checkPrime(numbers: number[]): string {
    if (!numbers || numbers.length === 0) {
        return 'No numbers provided to check.';
    }

    const primes = numbers.filter(n => isPrime(n));

    if (primes.length === 0) {
        console.log(`No prime numbers found in: ${numbers}`);
        return 'None of the numbers are prime.';
    }

    const result = `${primes.join(', ')} are prime numbers.`;
    console.log(`Prime check for ${numbers}: ${result}`);
    return result;
}

/**
 * Checks if a number is prime.
 * 
 * @param n - The number to check
 * @returns True if the number is prime, false otherwise
 */
function isPrime(n: number): boolean {
    if (n <= 1) {
        return false;
    }
    if (n === 2) {
        return true;
    }
    if (n % 2 === 0) {
        return false;
    }

    const sqrtN = Math.floor(Math.sqrt(n));
    for (let i = 3; i <= sqrtN; i += 2) {
        if (n % i === 0) {
            return false;
        }
    }

    return true;
}
