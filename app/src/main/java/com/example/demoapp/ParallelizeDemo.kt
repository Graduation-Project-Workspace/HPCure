package com.example.demoapp

import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

class ParallelizeDemo {
    fun main(jobCount : Int, ops : Int) = runBlocking {
        val time = measureTimeMillis {
            val jobs = List(jobCount) { async(Dispatchers.Default) { task1(ops) } }
            val results = jobs.awaitAll()
        }
    }
    fun runIdleTasks(jobCount: Int, idleTime: Int) = runBlocking {
        val time = measureTimeMillis {
            val jobs = List(jobCount) { async(Dispatchers.Default) { task2(idleTime) } }
            val results = jobs.awaitAll()
        }
    }
    suspend fun task2(idleTime: Int): Int {
        delay(idleTime.toLong())
        return 10
    }
    suspend fun task1(ops : Int): Int {
        var sum = 0;
        for(i in 1..ops)
            sum += i
        return 10
    }
}