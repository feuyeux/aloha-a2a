package main

import (
	"fmt"
	"math/rand"
	"strings"
	"time"
)

func init() {
	rand.Seed(time.Now().UnixNano())
}

// RollDice rolls an N-sided dice and returns the result
func RollDice(sides int) (int, error) {
	if sides <= 0 {
		return 0, fmt.Errorf("dice must have at least 1 side")
	}
	
	result := rand.Intn(sides) + 1
	return result, nil
}

// CheckPrime checks which numbers in the list are prime
func CheckPrime(numbers []int) string {
	if len(numbers) == 0 {
		return "No numbers provided to check."
	}
	
	var primes []int
	for _, n := range numbers {
		if isPrime(n) {
			primes = append(primes, n)
		}
	}
	
	if len(primes) == 0 {
		return "None of the numbers are prime."
	}
	
	// Convert primes to string
	primeStrs := make([]string, len(primes))
	for i, p := range primes {
		primeStrs[i] = fmt.Sprintf("%d", p)
	}
	
	return strings.Join(primeStrs, ", ") + " are prime numbers."
}

// isPrime checks if a number is prime
func isPrime(n int) bool {
	if n <= 1 {
		return false
	}
	if n == 2 {
		return true
	}
	if n%2 == 0 {
		return false
	}
	
	// Check odd divisors up to sqrt(n)
	for i := 3; i*i <= n; i += 2 {
		if n%i == 0 {
			return false
		}
	}
	
	return true
}
