package com.example.parallel_transaction_deadlock.service

import com.example.parallel_transaction_deadlock.domain.PostId

interface ProductBatchCreateUseCase {

    fun invoke(commands: List<Command>): List<Result>

    data class Command(
        val name: String,
        val description: String,
    )

    sealed class Result {
        data class Success(val postId: PostId) : Result()
        data class Failure(
            val name: String,
            val message: String
        ) : Result()
    }
}
