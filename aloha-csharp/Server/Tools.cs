using System.ComponentModel;
using Microsoft.SemanticKernel;

namespace Aloha.A2A.Server;

/// <summary>
/// Tools available to the Dice Agent
/// </summary>
public class Tools
{
    private static readonly Random Random = new();

    /// <summary>
    /// Rolls an N-sided dice and returns the result
    /// </summary>
    /// <param name="sides">Number of sides on the dice (must be positive)</param>
    /// <returns>Random number between 1 and N (inclusive)</returns>
    [KernelFunction("roll_dice")]
    [Description("Rolls an N-sided dice and returns a random number between 1 and N")]
    public int RollDice(
        [Description("Number of sides on the dice")] int sides)
    {
        if (sides <= 0)
        {
            throw new ArgumentException("Number of sides must be positive", nameof(sides));
        }

        return Random.Next(1, sides + 1);
    }

    /// <summary>
    /// Checks which numbers in the provided list are prime
    /// </summary>
    /// <param name="numbers">Comma-separated list of integers to check</param>
    /// <returns>String describing which numbers are prime</returns>
    [KernelFunction("check_prime")]
    [Description("Checks which numbers in a list are prime numbers")]
    public string CheckPrime(
        [Description("Comma-separated list of integers to check")] string numbers)
    {
        if (string.IsNullOrWhiteSpace(numbers))
        {
            return "No numbers provided.";
        }

        var nums = numbers.Split(',')
            .Select(s => s.Trim())
            .Where(s => int.TryParse(s, out _))
            .Select(int.Parse)
            .ToList();

        if (nums.Count == 0)
        {
            return "No valid numbers provided.";
        }

        var primes = nums.Where(IsPrime).ToList();

        if (primes.Count == 0)
        {
            return "None of the numbers are prime.";
        }

        return $"{string.Join(", ", primes)} are prime numbers.";
    }

    /// <summary>
    /// Determines if a number is prime
    /// </summary>
    private static bool IsPrime(int number)
    {
        if (number <= 1) return false;
        if (number == 2) return true;
        if (number % 2 == 0) return false;

        var boundary = (int)Math.Floor(Math.Sqrt(number));
        for (int i = 3; i <= boundary; i += 2)
        {
            if (number % i == 0) return false;
        }

        return true;
    }
}
