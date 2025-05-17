package com.example.demoapp

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis

class ConcurrencyDemo {
    fun main(jobCount : Int, ops : Int) = runBlocking {
        val time = measureTimeMillis {
            val jobs = List(jobCount) { async { task1(ops) } }
            val results = jobs.awaitAll()
        }
        Log.d("ExecutionTime", "Execution time (Concurrency), $jobCount jobs, $ops ops: $time ms");
    }
    fun runIdleTasks(jobCount: Int, idleTime: Int) = runBlocking {
        val time = measureTimeMillis {
            val jobs = List(jobCount) { async { task2(idleTime) } }
            val results = jobs.awaitAll()
        }
        Log.d("ExecutionTime", "Execution time (Concurrency), $jobCount jobs, $idleTime idle time: $time ms");
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