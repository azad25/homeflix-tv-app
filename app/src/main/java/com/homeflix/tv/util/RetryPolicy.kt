package com.homeflix.tv.util

import kotlinx.coroutines.delay
import kotlin.math.pow

/**
 * Retry policy for API calls with exponential backoff
 */
object RetryPolicy {
    
    /**
     * Execute a suspending function with retry logic
     * 
     * @param maxRetries Maximum number of retry attempts (default: 3)
     * @param initialDelayMs Initial delay before first retry in milliseconds (default: 1000ms)
     * @param maxDelayMs Maximum delay between retries (default: 10000ms)
     * @param factor Exponential backoff factor (default: 2.0)
     * @param block The suspending function to execute
     * @return Result of the operation
     */
    suspend fun <T> executeWithRetry(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000L,
        maxDelayMs: Long = 10000L,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                // Don't retry on last attempt
                if (attempt == maxRetries - 1) {
                    throw e
                }
                
                // Log retry attempt
                android.util.Log.w(
                    "RetryPolicy",
                    "Attempt ${attempt + 1} failed, retrying in ${currentDelay}ms: ${e.message}"
                )
                
                // Wait before retry with exponential backoff
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
            }
        }
        
        // This should never be reached, but needed for compilation
        return block()
    }
    
    /**
     * Execute with retry for network operations (shorter delays)
     */
    suspend fun <T> executeNetworkWithRetry(
        maxRetries: Int = 2,
        block: suspend () -> T
    ): T = executeWithRetry(
        maxRetries = maxRetries,
        initialDelayMs = 500L,
        maxDelayMs = 2000L,
        factor = 2.0,
        block = block
    )
}
