package main

import "math/rand"

func GenerateRandomTextByLength(length int) string {
	const charset = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 "
	result := make([]byte, length)
	for i := range result {
		result[i] = charset[rand.Intn(len(charset))]
	}
	return string(result)
}

const minCharLength = 100
const maxCharLength = 1000

func GenerateRandomText() string {
	length := rand.Intn(maxCharLength-minCharLength+1) + minCharLength
	return GenerateRandomTextByLength(length)
}