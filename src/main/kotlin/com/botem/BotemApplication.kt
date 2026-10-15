package com.botem

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BotemApplication

fun main(args: Array<String>) {
	runApplication<BotemApplication>(*args)
}
